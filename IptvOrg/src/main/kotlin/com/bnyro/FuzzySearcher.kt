package com.bnyro

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


class FuzzySearcher(private val fuzzyRanker: FuzzyRanker) {
    /**
     * Search for a [query] in the given [dataList].
     *
     * @param query the query to search for
     * @param dataList the list of data items to search in
     * @param maxResultCount the maximum amount of returned results
     * @param minConfidence the minimum confidence for the [dataList] item to be relevant, between 0 and 1
     * @param stringRepresentation convert an item of [dataList] to a string representation that is used to match against [query] by calculating their similarity.
     *
     * @return a list of results, sorted by their priority (the best result is the first item in the returned list)
     */
    fun <T> searchFuzzy(
        query: String,
        dataList: List<T>,
        maxResultCount: Int = 15,
        minConfidence: Float = 0.5f,
        stringRepresentation: (T) -> String
    ): List<T> {
        return dataList.map { data -> data to fuzzyRanker.similarity(query, stringRepresentation(data)) }
            .sortedByDescending { (_, similarity) -> similarity }
            .takeWhile { (_, similarity) -> similarity > minConfidence }
            .map { (data, _) -> data }
            .take(maxResultCount)
    }
}

interface FuzzyRanker {
    /**
     * Generate a value between 0 and 1, whereas 1 means that the
     * strings are exactly the same and 0 that they have no overlap.
     */
    fun similarity(query: String, candidate: String): Float
}

/**
 * Similar to Jaro Ranker, however exact substring (contains) matches
 * are ranked higher than with normal Jaro ranking.
 */
class ContainsJaroRanker : SortedJaroRanker() {
    override fun similarity(query: String, candidate: String): Float {
        val jaroSimilarity = super.similarity(query, candidate)

        // increase ranking if the candidate fully contains the search query
        // the longer the query, the higher the score improvement
        if (candidate.contains(query, ignoreCase = true)) {
            val scalingFactor = 1.1f.pow(query.length)
            val minRanking = min(0.4f * scalingFactor, 0.7f)
            return min(max(jaroSimilarity * scalingFactor, minRanking), 1.0f)
        }

        return jaroSimilarity
    }
}

open class SortedJaroRanker : JaroRanker() {
    override fun similarity(query: String, candidate: String): Float {
        val str1Fixed = sortWords(query.filter { it.isLetterOrDigit() }).lowercase()
        val str2Fixed = sortWords(candidate.filter { it.isLetterOrDigit() }).lowercase()
        return super.similarity(str1Fixed, str2Fixed)
    }

    /**
     * Sort the words inside a string alphabetically.
     */
    private fun sortWords(str: String): String {
        return str.split(" ").sorted().joinToString(" ")
    }

}

open class JaroRanker : FuzzyRanker {
    /**
     * Get the maximum length sequence of characters of [str1] that are also part of [str2] in the given [maxDistance].
     */
    private fun buildMatchingChars(str1: String, str2: String, maxDistance: Int): String {
        val matchingSequence = StringBuilder()

        // all already used chars are set to 0
        val str2Clone = StringBuilder(str2)

        for (i in 0 until str1.length) {
            for (j in max(0, i - maxDistance) until min(str2.length, i + maxDistance + 1)) {
                if (str1[i] == str2Clone[j]) {
                    str2Clone[j] = Char.MIN_VALUE
                    matchingSequence.append(str1[i])
                    break
                }
            }
        }

        return matchingSequence.toString()
    }

    /**
     * Apply the Jaro Similarity formula.
     */
    private fun calculateJaroSimilarity(
        str1: String,
        str2: String,
        matchingChars: Int,
        transpositions: Int
    ): Float {
        if (matchingChars == 0) return 0f

        val sum =
            matchingChars.toFloat() / str1.length + matchingChars.toFloat() / str2.length + (matchingChars - transpositions).toFloat() / matchingChars

        return sum / 3
    }

    /**
     * Calculate the jaro similarity between [str1] and [str2]
     */
    private fun jaroSimilarity(str1: String, str2: String): Float {
        val maxDistance = max(str1.length, str2.length) / 2 - 1
        val matchingCharsLeft = buildMatchingChars(str1, str2, maxDistance)
        val matchingCharsRight = buildMatchingChars(str2, str1, maxDistance)

        val transpositionCount = matchingCharsLeft.zip(matchingCharsRight)
            .count { (l, r) -> l != r } / 2

        return calculateJaroSimilarity(str1, str2, matchingCharsLeft.length, transpositionCount)
    }

    override fun similarity(query: String, candidate: String): Float {
        return jaroSimilarity(query, candidate)
    }
}