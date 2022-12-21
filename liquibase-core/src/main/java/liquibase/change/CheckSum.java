package liquibase.change;

import liquibase.util.MD5Util;
import liquibase.util.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CheckSums are used by liquibase to determine if a Change has been modified since it was originally ran.
 * CheckSums can be computed on either a String or an {@link InputStream}.
 * The CheckSum contains a version number which can be used to determine whether the algorithm for computing a
 * storedCheckSum has changed since the last time it was computed. If the algorithm changes, we cannot rely on
 * the storedCheckSum value.
 * <p></p>
 * It is not up to this class to determine what should be storedCheckSum-ed, it simply hashes what is passed to it.
 */
public final class CheckSum {
    private int version;
    private String storedCheckSum;

    private static final int CURRENT_CHECKSUM_ALGORITHM_VERSION = 8;
    private static final char DELIMITER = ':';
    private static final Pattern CHECKSUM_PATTERN = Pattern.compile("(^\\d)" + DELIMITER + "([a-zA-Z0-9]++)");

    /**
     * Constructor. Stores a given checksum generated by a given algorithm version into the new CheckSum object.
     *
     * @param checksum Generated checksum (format depends on version)
     * @param version  The version of the Liquibase checksum generator used
     */
    private CheckSum(String checksum, int version) {
        this.storedCheckSum = checksum;
        this.version = version;
    }

    /**
     * Parse the given storedCheckSum string value and return a new CheckSum object.
     */
    public static CheckSum parse(String checksumValue) {
        if (checksumValue == null) {
            return null;
        }
        // The general layout of a checksum is:
        // <1 digit: algorithm version number>:<1..n characters alphanumeric checksum>
        // Example: 7:2cdf9876e74347162401315d34b83746
        Matcher matcher = CHECKSUM_PATTERN.matcher(checksumValue);
        if (matcher.find()) {
            return new CheckSum(matcher.group(2), Integer.parseInt(matcher.group(1)));
        } else {
            // No version information found
            return new CheckSum(checksumValue, 1);
        }
    }

    /**
     * Return the current CheckSum algorithm version.
     */
    public static int getCurrentVersion() {
        return CURRENT_CHECKSUM_ALGORITHM_VERSION;
    }

    /**
     * Compute a storedCheckSum of the given string.
     */
    public static CheckSum compute(String valueToChecksum) {
        return new CheckSum(MD5Util.computeMD5(
                //remove "Unknown" unicode char 65533
                Normalizer.normalize(StringUtil.standardizeLineEndings(valueToChecksum)
                        .replace("\uFFFD", ""), Normalizer.Form.NFC)
        ), getCurrentVersion());
    }

    /**
     * Compute a CheckSum of the given data stream (no normalization of line endings!)
     */
    public static CheckSum compute(final InputStream stream, boolean standardizeLineEndings) {
        InputStream newStream = stream;
        if (standardizeLineEndings) {
            newStream = new InputStream() {
                private boolean isPrevR = false;

                @Override
                public int read() throws IOException {
                    int read = stream.read();

                    if (read == '\r') {
                        isPrevR = true;
                        return '\n';
                    } else if (read == '\n' && isPrevR) {
                        isPrevR = false;
                        return read();
                    } else {
                        isPrevR = false;
                        return read;
                    }
                }
            };
        }

        return new CheckSum(MD5Util.computeMD5(newStream), getCurrentVersion());
    }

    @Override
    public String toString() {
        return version + String.valueOf(DELIMITER) + storedCheckSum;
    }

    /**
     * Return the Checksum Algorithm version for this CheckSum
     */
    public int getVersion() {
        return version;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof CheckSum) && this.toString().equals(obj.toString());
    }
}
