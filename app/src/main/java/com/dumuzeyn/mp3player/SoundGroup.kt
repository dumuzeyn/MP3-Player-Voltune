package com.dumuzeyn.mp3player

class SoundGroup(
    id: String?,
    nameRussian: String?,
    nameEnglish: String?,
    centroid: DoubleArray?,
    trackIds: List<String>?,
) {
    @JvmField val id: String = id.orEmpty()
    @JvmField val nameRussian: String = nameRussian.orEmpty()
    @JvmField val nameEnglish: String = nameEnglish.orEmpty()
    @JvmField val centroid: DoubleArray = centroid?.clone() ?: DoubleArray(0)
    @JvmField val trackIds: ArrayList<String> = ArrayList(trackIds.orEmpty())

    fun named(russian: String?, english: String?): SoundGroup =
        SoundGroup(id, russian, english, centroid, trackIds)
}
