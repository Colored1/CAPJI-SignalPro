package com.capji.signalpro.core

import java.security.SecureRandom
import kotlin.math.ln
import kotlin.math.pow

data class PasswordOptions(
    val length: Int = 16,
    val lowercase: Boolean = true,
    val uppercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = true,
)

enum class Strength { WEAK, FAIR, STRONG, EXCELLENT }

object PasswordGenerator {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 63

    /** Assumed offline attack speed used for the crack-time estimate. */
    const val GUESSES_PER_SECOND = 1e9

    private const val AMBIGUOUS = "O0oIl1|"

    fun characterSets(options: PasswordOptions): List<String> {
        val sets = listOfNotNull(
            "abcdefghijklmnopqrstuvwxyz".takeIf { options.lowercase },
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ".takeIf { options.uppercase },
            "0123456789".takeIf { options.digits },
            "!@#\$%^&*()-_=+[]{}?".takeIf { options.symbols },
        )
        return if (!options.avoidAmbiguous) sets
        else sets.map { set -> set.filterNot { it in AMBIGUOUS } }
    }

    /** Every selected character set appears at least once. */
    fun generate(options: PasswordOptions, random: SecureRandom = SecureRandom()): String {
        val sets = characterSets(options)
        require(sets.isNotEmpty()) { "Select at least one character set" }
        val length = options.length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val pool = sets.joinToString("")
        // Rejection sampling keeps the result uniform over all passwords that satisfy the rule.
        while (true) {
            val candidate = String(CharArray(length) { pool[random.nextInt(pool.length)] })
            if (sets.all { set -> candidate.any { it in set } }) return candidate
        }
    }

    fun entropyBits(length: Int, poolSize: Int): Double =
        if (poolSize <= 1 || length <= 0) 0.0 else length * (ln(poolSize.toDouble()) / ln(2.0))

    fun strength(bits: Double): Strength = when {
        bits < 45 -> Strength.WEAK
        bits < 65 -> Strength.FAIR
        bits < 90 -> Strength.STRONG
        else -> Strength.EXCELLENT
    }

    /** Average time to find the password: half of the key space. */
    fun crackSeconds(bits: Double): Double = 2.0.pow(bits - 1) / GUESSES_PER_SECOND
}
