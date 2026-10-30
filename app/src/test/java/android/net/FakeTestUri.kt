
package android.net

class FakeTestUri(
    private val uriString: String = "content://test/backup.pcbk",
) : Uri() {
    override fun toString(): String = uriString

    override fun isHierarchical(): Boolean = true

    override fun isRelative(): Boolean = false

    override fun getScheme(): String = "content"

    override fun getSchemeSpecificPart(): String = "//test/backup.pcbk"

    override fun getEncodedSchemeSpecificPart(): String = "//test/backup.pcbk"

    override fun getAuthority(): String = "test"

    override fun getEncodedAuthority(): String = "test"

    override fun getUserInfo(): String? = null

    override fun getEncodedUserInfo(): String? = null

    override fun getHost(): String = "test"

    override fun getPort(): Int = -1

    override fun getPath(): String = "/backup.pcbk"

    override fun getEncodedPath(): String = "/backup.pcbk"

    override fun getQuery(): String? = null

    override fun getEncodedQuery(): String? = null

    override fun getFragment(): String? = null

    override fun getEncodedFragment(): String? = null

    override fun getPathSegments(): List<String> = listOf("backup.pcbk")

    override fun getLastPathSegment(): String = "backup.pcbk"

    override fun buildUpon(): Builder = throw UnsupportedOperationException()

    override fun describeContents(): Int = 0

    override fun writeToParcel(
        dest: android.os.Parcel,
        flags: Int,
    ) {
        // no-op
    }

    override fun compareTo(other: Uri?): Int = 0
}
