package com.example.utils.theewaysmerge

import org.apache.commons.io.output.ByteArrayOutputStream
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.merge.MergeAlgorithm
import org.eclipse.jgit.merge.MergeFormatter

class ThreeWayMerge {
    fun merge(base: String, user: String, server: String): String {
        val baseTxt = RawText(base.toByteArray(Charsets.UTF_8))
        val userTxt = RawText(user.toByteArray(Charsets.UTF_8))
        val serverTxt = RawText(server.toByteArray(Charsets.UTF_8))

        val result = MergeAlgorithm()
            .merge(RawTextComparator.DEFAULT, baseTxt, serverTxt, userTxt)

        val output = ByteArrayOutputStream()
        MergeFormatter().formatMerge(output, result, listOf("BASE", "SERVER", "USER"), Charsets.UTF_8)
        val mergedText = output.toString(Charsets.UTF_8.name())
        return mergedText
    }
}