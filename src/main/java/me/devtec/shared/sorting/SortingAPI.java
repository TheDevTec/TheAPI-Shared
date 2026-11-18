package me.devtec.shared.sorting;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * Adaptive integer sorting. Handles every signed int value and both directions.
 * No public Workspace. Concurrent invocations exclusively borrow their
 * own scratch memory. The cache holds at most one array (up to 16 MiB).
 *
 * Production sorting uses an adaptive compressed-bucket algorithm with
 * occupancy reconstruction for sparse values and compact counters for dense
 * ranges. The public API exposes only stable sorting methods.
 * All 32-bit signed integers are supported in both directions.
 */
public final class SortingAPI {
    private static final int SMALL_LIMIT = 2048;
    private static final int PARALLEL_LIMIT = 2_000_000;
    private static final int FAST_BITS = 13;
    private static final int FAST_BINS = 1 << FAST_BITS;
    private static final int FAST_MASK = FAST_BINS - 1;
    private static final int FAST_KEY_MASK = (1 << 26) - 1;
    private static final int FULL_BINS = 1 << 16;
    private static final int FULL_MASK = FULL_BINS - 1;
    private static final int BUCKET_HIGH_BINS = 1 << 10;
    private static final int BUCKET_HIGH_MASK = BUCKET_HIGH_BINS - 1;
    private static final int BUCKET_HIST_STRIDE = BUCKET_HIGH_BINS + 16;

    private static final int MAX_CACHED_CHARS = 8_388_608;
    private static final int MIN_CACHED_CHARS = 4_000_000;
    private static final AtomicReference<char[]> BUCKET_SCRATCH = new AtomicReference<>();

    private static char[] borrowBucketScratch(int n) {
        if (n < MIN_CACHED_CHARS || n > MAX_CACHED_CHARS) return new char[n];
        char[] existing = BUCKET_SCRATCH.getAndSet(null);
        if (existing == null) return new char[n];
        if (existing.length >= n) return existing;
        // The old scratch was too small; recycle the new larger buffer instead.
        return new char[n];
    }

    private static void recycleBucketScratch(char[] array) {
        if (array.length >= MIN_CACHED_CHARS && array.length <= MAX_CACHED_CHARS)
            BUCKET_SCRATCH.compareAndSet(null, array);
    }

    private SortingAPI() {}

    /** Sorts signed integers in ascending order. */
    public static void sortAscending(int[] array) {
        sortPrimitiveBucket(array, true);
    }

    /** Sorts signed integers in descending order. */
    public static void sortDescending(int[] array) {
        sortPrimitiveBucket(array, false);
    }

    /** Sorts signed integers using the optimized adaptive bucket algorithm. */
    public static void sort(int[] array, boolean ascending) {
        sortPrimitiveBucket(array, ascending);
    }

    private static void sortInternal(int[] array, boolean ascending) {
        int n = array.length;
        if (n < 2) return;
        if (n <= SMALL_LIMIT) {
            Arrays.sort(array);
            if (!ascending) reverse(array);
            return;
        }

        // A random array almost always changes direction after a few values.
        // For monotonic input this replaces the entire sort with a linear scan
        // (and a single in-place reversal when the requested order differs).
        if (finishIfMonotonic(array, ascending)) return;

        int first = array[0];
        int sampleMin = first;
        int sampleMax = first;
        int varying = 0;
        int sampleEnd = Math.min(n, 64);
        for (int i = 1; i < sampleEnd; i++) {
            int v = array[i];
            varying |= v ^ first;
            if (v < sampleMin) sampleMin = v;
            if (v > sampleMax) sampleMax = v;
        }

        // Dense narrow value ranges are better served by a histogram, not radix.
        if ((long) sampleMax - sampleMin < 16_384L) {
            int min = first, max = first;
            for (int v : array) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
            long width = (long) max - min + 1L;
            if (width <= 262_144L && width <= n / 2L) {
                counting(array, min, (int) width, ascending);
                return;
            }
        }

        final int signedFlip = ascending ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        final boolean parallel = n >= PARALLEL_LIMIT
                && Runtime.getRuntime().availableProcessors() >= 2
                && ForkJoinPool.getCommonPoolParallelism() >= 1;

        // Fast path 1: all values have identical top 6 bits. This covers
        // 0..50M, -50M..-1, and many other narrow positive/negative ranges.
        if ((varying >>> 26) == 0) {
            if (parallel) {
                if (parallelBucket26(array, first & ~FAST_KEY_MASK,
                        ascending ? 0 : FAST_KEY_MASK, first, 1)) return;
            } else if (radix13(array, 0, signedFlip, first, 1)) return;
        } else {
            // Fast path 2: a 26-bit *relative* range, including values that
            // cross zero or straddle a 26-bit boundary. A small sample only
            // proposes an offset; the complete histogram scan validates it.
            long sampleWidth = (long) sampleMax - sampleMin;
            if (sampleWidth < (1L << 26)) {
                long margin = Math.max(1024L, sampleWidth >>> 3);
                long lower = Math.max((long) Integer.MIN_VALUE,
                                      (long) sampleMin - margin);
                if ((long) sampleMax - lower < (1L << 26)) {
                    int base = (int) lower;
                    int xor = ascending ? 0 : FAST_KEY_MASK;
                    if (parallel) {
                        if (parallelBucket26(array, base, xor, first, 2)) return;
                    } else if (radix13(array, base, xor, first, 2)) return;
                }
            }
        }

        // All 32 signed bits, including MIN_VALUE and MAX_VALUE.
        if (parallel) {
            parallelBucketSparse(array, Integer.MIN_VALUE,
                    ascending ? 0 : -1, 16, false);
        } else {
            radix16(array, signedFlip);
        }
    }

    /**
     * Production primitive sorting: dense buckets use direct counters,
     * sparse buckets use occupancy maps. Very wide distributions use the
     * full 32-bit bucket reconstruction. All arrays are per-sort/worker.
     */
    private static void sortPrimitiveBucket(int[] array, boolean ascending) {
        final int n = array.length;
        if (n < 2) return;
        if (n < PARALLEL_LIMIT || workerCount(n) < 2) {
            sortInternal(array, ascending);
            return;
        }
        if (finishIfMonotonic(array, ascending)) return;

        final int first = array[0];
        int sampleMin = first, sampleMax = first, variation = 0;
        final int sampleEnd = Math.min(n, 64);
        for (int i = 1; i < sampleEnd; i++) {
            int value = array[i];
            variation |= value ^ first;
            if (value < sampleMin) sampleMin = value;
            if (value > sampleMax) sampleMax = value;
        }
        final long sampledSpan = (long) sampleMax - sampleMin;
        // Dense low-digit buckets can profit from direct counters. Sparse
        // buckets use the sparse occupancy reconstruction.
        final boolean denseCandidate = sampledSpan >= 0 &&
                (long) n * 2 >= sampledSpan + 1;
        if ((variation >>> 26) == 0) {
            int base = first & ~FAST_KEY_MASK;
            int xor = ascending ? 0 : FAST_KEY_MASK;
            if (denseCandidate
                    ? parallelBucketAdaptive26(array, base, xor, first, 1)
                    : parallelBucket26(array, base, xor, first, 1)) return;
        } else if (sampledSpan < (1L << 26)) {
            long width = (long) sampleMax - sampleMin;
            long margin = Math.max(1024L, width >>> 3);
            long lower = Math.max((long) Integer.MIN_VALUE,
                    (long) sampleMin - margin);
            if ((long) sampleMax - lower < (1L << 26)) {
                int base = (int) lower;
                int xor = ascending ? 0 : FAST_KEY_MASK;
                if (denseCandidate
                        ? parallelBucketAdaptive26(array, base, xor, first, 2)
                        : parallelBucket26(array, base, xor, first, 2)) return;
            }
        }

        // Favor adaptive reconstruction for intermediate distributions;
        // use sparse full-width reconstruction for very broad inputs.
        // Sampling only selects performance strategy, never correctness.
        if (sampledSpan < 1_000_000_000L) {
            parallelBucketAdaptive32(array, Integer.MIN_VALUE,
                    ascending ? 0 : -1);
        } else {
            parallelBucketSparse(array, Integer.MIN_VALUE,
                    ascending ? 0 : -1, 16, false);
        }
    }

    /**
     * Non-radix, two-level bucket distribution.
     * Each bucket is reconstructed using a 16-bit occupancy bitmap and a
     * 16-bit hierarchy of occupied bitmap-word groups. Empty words are never
     * scanned. Only one O(n) short/char scratch array is needed.
     *
     * The production path uses 16 high bits for the full signed-int range.
     */
    private static boolean parallelBucketSparse(int[] array, int base, int xor,
                                                int highBits, boolean validateRange) {
        final int n = array.length;
        final int workers = workerCount(n);
        final int bins = 1 << highBits;
        final int stride = bins + 16; // pad worker counters against false sharing
        final int mask = bins - 1;
        final int[] positions = new int[workers * stride];
        final int[] minimum = validateRange ? new int[workers] : null;
        final int[] maximum = validateRange ? new int[workers] : null;

        parallelFor(workers, worker -> {
            final int from = (int) ((long) n * worker / workers);
            final int to = (int) ((long) n * (worker + 1) / workers);
            final int offset = worker * stride;
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int i = from; i < to; ++i) {
                int value = array[i];
                int key = (value - base) ^ xor;
                positions[offset + ((key >>> 16) & mask)]++;
                if (validateRange) {
                    if (value < lo) lo = value;
                    if (value > hi) hi = value;
                }
            }
            if (validateRange) {
                minimum[worker] = lo;
                maximum[worker] = hi;
            }
        });

        if (validateRange) {
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int w = 0; w < workers; ++w) {
                if (minimum[w] < lo) lo = minimum[w];
                if (maximum[w] > hi) hi = maximum[w];
            }
            if ((long) lo - base < 0 || (long) hi - base >= (1L << 29))
                return false;
        }

        final int[] starts = new int[bins + 1];
        int total = 0;
        for (int b = 0; b < bins; ++b) {
            starts[b] = total;
            for (int w = 0; w < workers; ++w) {
                int idx = w * stride + b;
                int count = positions[idx];
                positions[idx] = total;
                total += count;
            }
        }
        starts[bins] = n;

        char[] scratch = borrowBucketScratch(n);
        try {
            parallelFor(workers, worker -> {
                int from = (int) ((long) n * worker / workers);
                int to = (int) ((long) n * (worker + 1) / workers);
                int offset = worker * stride;
                for (int i = from; i < to; ++i) {
                    int key = (array[i] - base) ^ xor;
                    int bucket = (key >>> 16) & mask;
                    scratch[positions[offset + bucket]++] = (char) key;
                }
            });

            // All buckets are independent; assign contiguous ranges to workers.
            final int[] cuts = new int[workers + 1];
            for (int w = 1; w < workers; ++w) {
                int target = (int) ((long) n * w / workers);
                int b = cuts[w - 1];
                while (b < bins && starts[b] < target) ++b;
                cuts[w] = b;
            }
            cuts[workers] = bins;

            parallelFor(workers, worker -> {
                final char[] duplicateCounts = new char[1 << 16];
                int[] overflowCounts = null;
                final long[] occupied = new long[1024];
                final long[] occupiedWords = new long[16];

                for (int bucket = cuts[worker]; bucket < cuts[worker + 1]; ++bucket) {
                    int begin = starts[bucket];
                    int end = starts[bucket + 1];
                    if (begin == end) continue;

                    int usedGroups = 0;
                    for (int i = begin; i < end; ++i) {
                        int low = scratch[i];
                        int word = low >>> 6;
                        long bit = 1L << (low & 63);
                        long bits = occupied[word];
                        if ((bits & bit) == 0L) {
                            if (bits == 0L) {
                                int group = word >>> 6;
                                occupiedWords[group] |= 1L << (word & 63);
                                usedGroups |= 1 << group;
                            }
                            occupied[word] = bits | bit;
                        } else {
                            int count = duplicateCounts[low];
                            if (count < 0xffff) {
                                duplicateCounts[low] = (char) (count + 1);
                            } else {
                                if (overflowCounts == null) overflowCounts = new int[1 << 16];
                                overflowCounts[low]++;
                            }
                        }
                    }

                    int out = begin;
                    while (usedGroups != 0) {
                        int group = Integer.numberOfTrailingZeros(usedGroups);
                        usedGroups &= usedGroups - 1;
                        long words = occupiedWords[group];
                        occupiedWords[group] = 0L;
                        while (words != 0) {
                            int word = (group << 6) + Long.numberOfTrailingZeros(words);
                            words &= words - 1;
                            long bits = occupied[word];
                            occupied[word] = 0L; // Clear only touched words.
                            while (bits != 0) {
                                int low = (word << 6) + Long.numberOfTrailingZeros(bits);
                                bits &= bits - 1;
                                int duplicates = duplicateCounts[low];
                                if (overflowCounts != null) duplicates += overflowCounts[low];
                                int value = (((bucket << 16) | low) ^ xor) + base;
                                if (duplicates == 0) {
                                    array[out++] = value;
                                } else {
                                    duplicateCounts[low] = 0;
                                    if (overflowCounts != null) overflowCounts[low] = 0;
                                    int count = duplicates + 1;
                                    if (count >= 8) {
                                        Arrays.fill(array, out, out + count, value);
                                        out += count;
                                    } else {
                                        while (count-- > 0) array[out++] = value;
                                    }
                                }
                            }
                        }
                    }
                }
            });
            return true;
        } finally {
            recycleBucketScratch(scratch);
        }
    }

    /**
     * Adaptive reconstruction. When a high bucket is dense, scanning
     * a flat histogram avoids millions of occupancy/duplicate branches.
     * Otherwise retain the sparse bitmap path (including exact overflows).
     */
    private static void reconstructAdaptiveBucket(char[] scratch, int[] output,
            int begin, int end, int bucket, int base, int xor,
            char[] duplicateCounts, long[] occupied,
            long[] occupiedWords, int[][] overflowHolder,
            int[][] denseHolder) {
        final int length = end - begin;
        if (length <= 0) return;

        // For a densely populated bucket (> ~half its 65536 slots), direct
        // counters are usually more cache-friendly. Reject constant/very
        // low diversity buckets where the sparse path is far cheaper.
        boolean dense = length >= 32_768;
        if (dense) {
            int first = scratch[begin];
            int different = 0;
            int step = Math.max(1, length >>> 5);
            for (int i = begin + step; i < end && different < 5; i += step) {
                if (scratch[i] != first) different++;
            }
            dense = different >= 5;
        }
        if (dense) {
            int[] counts = denseHolder[0];
            if (counts == null) denseHolder[0] = counts = new int[1 << 16];
            for (int i = begin; i < end; i++) counts[scratch[i]]++;
            int out = begin;
            for (int low = 0; low < (1 << 16); low++) {
                int count = counts[low];
                if (count == 0) continue;
                counts[low] = 0;
                int value = (((bucket << 16) | low) ^ xor) + base;
                if (count >= 8) {
                    Arrays.fill(output, out, out + count, value);
                    out += count;
                } else {
                    while (count-- > 0) output[out++] = value;
                }
            }
            return;
        }

        int usedGroups = 0;
        for (int i = begin; i < end; i++) {
            int low = scratch[i];
            int word = low >>> 6;
            long bit = 1L << (low & 63);
            long bits = occupied[word];
            if ((bits & bit) == 0) {
                if (bits == 0) {
                    int group = word >>> 6;
                    occupiedWords[group] |= 1L << (word & 63);
                    usedGroups |= 1 << group;
                }
                occupied[word] = bits | bit;
            } else {
                int count = duplicateCounts[low];
                if (count < 0xffff) duplicateCounts[low] = (char) (count + 1);
                else {
                    int[] over = overflowHolder[0];
                    if (over == null) overflowHolder[0] = over = new int[1 << 16];
                    over[low]++;
                }
            }
        }

        int out = begin;
        while (usedGroups != 0) {
            int group = Integer.numberOfTrailingZeros(usedGroups);
            usedGroups &= usedGroups - 1;
            long words = occupiedWords[group];
            occupiedWords[group] = 0;
            while (words != 0) {
                int word = (group << 6) + Long.numberOfTrailingZeros(words);
                words &= words - 1;
                long bits = occupied[word];
                occupied[word] = 0;
                while (bits != 0) {
                    int low = (word << 6) + Long.numberOfTrailingZeros(bits);
                    bits &= bits - 1;
                    int dup = duplicateCounts[low];
                    int[] over = overflowHolder[0];
                    if (over != null) dup += over[low];
                    int value = (((bucket << 16) | low) ^ xor) + base;
                    duplicateCounts[low] = 0;
                    if (over != null) over[low] = 0;
                    int count = dup + 1;
                    if (count >= 8) {
                        Arrays.fill(output, out, out + count, value);
                        out += count;
                    } else {
                        while (count-- > 0) output[out++] = value;
                    }
                }
            }
        }
    }

    /**
     * Full-width distribution. A histogram prefix decides whether
     * reconstruction should use contiguous cuts or balance unequal buckets
     * across workers. The 16-bit scratch representation is unchanged.
     */
    private static void parallelBucketAdaptive32(int[] array, int base, int xor) {
        final int n = array.length;
        final int workers = workerCount(n);
        final int bins = 1 << 16;
        final int stride = bins + 16;
        final int[] positions = new int[workers * stride];
        parallelFor(workers, worker -> {
            int from = (int) ((long) n * worker / workers);
            int to = (int) ((long) n * (worker + 1) / workers);
            int offset = worker * stride;
            for (int i = from; i < to; i++) {
                int key = (array[i] - base) ^ xor;
                positions[offset + (key >>> 16)]++;
            }
        });

        final int[] starts = new int[bins + 1];
        int total = 0;
        for (int b = 0; b < bins; b++) {
            starts[b] = total;
            for (int w = 0; w < workers; w++) {
                int index = w * stride + b;
                int count = positions[index];
                positions[index] = total;
                total += count;
            }
        }
        starts[bins] = n;

        char[] scratch = borrowBucketScratch(n);
        try {
            parallelFor(workers, w -> {
                int from = (int) ((long) n * w / workers);
                int to = (int) ((long) n * (w + 1) / workers);
                int offset = w * stride;
                for (int i = from; i < to; i++) {
                    int key = (array[i] - base) ^ xor;
                    scratch[positions[offset + (key >>> 16)]++] = (char) key;
                }
            });

            final int[] cuts = new int[workers + 1];
            for (int w = 1; w < workers; w++) {
                int target = (int) ((long) n * w / workers);
                int b = cuts[w - 1];
                while (b < bins && starts[b] < target) b++;
                cuts[w] = b;
            }
            cuts[workers] = bins;
            long maxLoad = 0;
            for (int w = 0; w < workers; w++) {
                long load = starts[cuts[w + 1]] - starts[cuts[w]];
                if (load > maxLoad) maxLoad = load;
            }
            final boolean rebalance = maxLoad * workers > (long) n * 3 / 2;
            final int[] heads, links;
            if (rebalance) {
                heads = new int[workers];
                int[] tails = new int[workers];
                Arrays.fill(heads, -1);
                links = new int[bins];
                long[] loads = new long[workers];
                for (int b = 0; b < bins; b++) {
                    int length = starts[b + 1] - starts[b];
                    if (length == 0) continue;
                    int owner = 0;
                    for (int w = 1; w < workers; w++)
                        if (loads[w] < loads[owner]) owner = w;
                    loads[owner] += length + 128L;
                    links[b] = -1;
                    if (heads[owner] < 0) heads[owner] = b;
                    else links[tails[owner]] = b;
                    tails[owner] = b;
                }
            } else {
                heads = null;
                links = null;
            }

            parallelFor(workers, w -> {
                char[] duplicateCounts = new char[1 << 16];
                long[] occupied = new long[1 << 10];
                long[] occupiedWords = new long[16];
                int[][] over = new int[1][];
                int[][] dense = new int[1][];
                if (rebalance) {
                    for (int b = heads[w]; b >= 0; b = links[b]) {
                        reconstructAdaptiveBucket(scratch, array, starts[b], starts[b + 1],
                                b, base, xor, duplicateCounts, occupied, occupiedWords,
                                over, dense);
                    }
                } else {
                    for (int b = cuts[w]; b < cuts[w + 1]; b++) {
                        reconstructAdaptiveBucket(scratch, array, starts[b], starts[b + 1],
                                b, base, xor, duplicateCounts, occupied, occupiedWords,
                                over, dense);
                    }
                }
            });
        } finally {
            recycleBucketScratch(scratch);
        }
    }

    /**
     * Narrow-key variant: compressed distribution with adaptive
     * reconstruction for buckets with many repeated values.
     */
    private static boolean parallelBucketAdaptive26(int[] array, int base, int xor,
                                                     int first, int verifyMode) {
        final int workers = workerCount(array.length);
        if (workers < 2) return radix13(array, base, xor, first, verifyMode);
        final int n = array.length;
        final int bins = 1 << 10;
        final int stride = bins + 16;
        final int[] positions = new int[workers * stride];
        final int[] mins = new int[workers];
        final int[] maxs = new int[workers];
        parallelFor(workers, w -> {
            int from = (int) ((long) n * w / workers);
            int to = (int) ((long) n * (w + 1) / workers);
            int vmin = Integer.MAX_VALUE, vmax = Integer.MIN_VALUE;
            int offset = w * stride;
            for (int i = from; i < to; i++) {
                int value = array[i];
                if (value < vmin) vmin = value;
                if (value > vmax) vmax = value;
                positions[offset + ((((value - base) ^ xor) >>> 16) & (bins - 1))]++;
            }
            mins[w] = vmin;
            maxs[w] = vmax;
        });
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int w = 0; w < workers; w++) {
            if (mins[w] < min) min = mins[w];
            if (maxs[w] > max) max = maxs[w];
        }
        if (verifyMode == 1) {
            if ((min >>> 26) != (first >>> 26) ||
                    (max >>> 26) != (first >>> 26)) return false;
        } else if ((long) min - base < 0 ||
                   (long) max - base >= (1L << 26)) {
            return false;
        }

        final int[] starts = new int[bins + 1];
        int total = 0;
        for (int b = 0; b < bins; b++) {
            starts[b] = total;
            for (int w = 0; w < workers; w++) {
                int index = w * stride + b;
                int count = positions[index];
                positions[index] = total;
                total += count;
            }
        }
        starts[bins] = n;
        char[] scratch = borrowBucketScratch(n);
        try {
            parallelFor(workers, w -> {
                int from = (int) ((long) n * w / workers);
                int to = (int) ((long) n * (w + 1) / workers);
                int offset = w * stride;
                for (int i = from; i < to; i++) {
                    int key = (array[i] - base) ^ xor;
                    int bucket = (key >>> 16) & (bins - 1);
                    scratch[positions[offset + bucket]++] = (char) key;
                }
            });
            final int[] cuts = new int[workers + 1];
            for (int w = 1; w < workers; w++) {
                int target = (int) ((long) n * w / workers);
                int b = cuts[w - 1];
                while (b < bins && starts[b] < target) b++;
                cuts[w] = b;
            }
            cuts[workers] = bins;
            parallelFor(workers, w -> {
                char[] duplicateCounts = new char[1 << 16];
                long[] occupied = new long[1 << 10];
                long[] occupiedWords = new long[16];
                int[][] over = new int[1][];
                int[][] dense = new int[1][];
                for (int b = cuts[w]; b < cuts[w + 1]; b++) {
                    reconstructAdaptiveBucket(scratch, array, starts[b], starts[b + 1],
                            b, base, xor, duplicateCounts, occupied, occupiedWords,
                            over, dense);
                }
            });
            return true;
        } finally {
            recycleBucketScratch(scratch);
        }
    }

    private static void counting(int[] array, int min, int width, boolean ascending) {
        int[] histogram = new int[width];
        for (int x : array) histogram[x - min]++;
        int out = 0;
        if (ascending) {
            for (int d = 0; d < width; d++) {
                int c = histogram[d];
                if (c >= 8) {
                    Arrays.fill(array, out, out + c, min + d);
                    out += c;
                } else while (c-- > 0) array[out++] = min + d;
            }
        } else {
            for (int d = width - 1; d >= 0; d--) {
                int c = histogram[d];
                if (c >= 8) {
                    Arrays.fill(array, out, out + c, min + d);
                    out += c;
                } else while (c-- > 0) array[out++] = min + d;
            }
        }
    }

    // verifyMode 1: check raw values share the same top 6 bits.
    // verifyMode 2: check all offset keys are unsigned 26-bit values.
    private static boolean radix13(int[] array, int base, int xor,
                                   int first, int verifyMode) {
        int[] position = new int[2 * FAST_BINS];
        int variation = 0;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int x : array) {
            int key = (x - base) ^ xor;
            if (verifyMode == 1) variation |= x ^ first;
            else {
                if (x < min) min = x;
                if (x > max) max = x;
            }
            position[key & FAST_MASK]++;
            position[FAST_BINS + ((key >>> FAST_BITS) & FAST_MASK)]++;
        }
        if (verifyMode == 1 && (variation >>> 26) != 0) return false;
        if (verifyMode == 2 && ((long) min - base < 0
                || (long) max - base >= (1L << 26))) return false;

        int sum = 0;
        for (int i = 0; i < FAST_BINS; i++) {
            int c = position[i]; position[i] = sum; sum += c;
        }
        int[] scratch = new int[array.length];
        for (int x : array) {
            int digit = ((x - base) ^ xor) & FAST_MASK;
            scratch[position[digit]++] = x;
        }
        sum = 0;
        for (int i = FAST_BINS; i < 2 * FAST_BINS; i++) {
            int c = position[i]; position[i] = sum; sum += c;
        }
        for (int x : scratch) {
            int digit = FAST_BINS + ((((x - base) ^ xor) >>> FAST_BITS) & FAST_MASK);
            array[position[digit]++] = x;
        }
        return true;
    }

    private static void radix16(int[] array, int xor) {
        int[] position = new int[2 * FULL_BINS];
        for (int x : array) {
            int key = x ^ xor;
            position[key & FULL_MASK]++;
            position[FULL_BINS + (key >>> 16)]++;
        }
        int sum = 0;
        for (int i = 0; i < FULL_BINS; i++) {
            int c = position[i]; position[i] = sum; sum += c;
        }
        int[] scratch = new int[array.length];
        for (int x : array) scratch[position[(x ^ xor) & FULL_MASK]++] = x;
        sum = 0;
        for (int i = FULL_BINS; i < 2 * FULL_BINS; i++) {
            int c = position[i]; position[i] = sum; sum += c;
        }
        for (int x : scratch) array[position[FULL_BINS + ((x ^ xor) >>> 16)]++] = x;
    }

    private static void parallelFor(int workers, IntConsumer action) {
        ForkJoinTask<?>[] futures = new ForkJoinTask<?>[workers - 1];
        ForkJoinPool pool = ForkJoinPool.commonPool();
        for (int w = 1; w < workers; w++) {
            final int worker = w;
            futures[w - 1] = pool.submit(() -> action.accept(worker));
        }
        Throwable error = null;
        try {
            action.accept(0);
        } catch (Throwable e) {
            error = e;
        }
        // Complete every submitted job before releasing the exclusive scratch
        // buffer, even when one worker or the caller encountered an exception.
        for (ForkJoinTask<?> future : futures) {
            try {
                future.join();
            } catch (Throwable e) {
                if (error == null) error = e;
                else if (error != e) error.addSuppressed(e);
            }
        }
        if (error != null) {
            if (error instanceof Error) throw (Error) error;
            if (error instanceof RuntimeException) throw (RuntimeException) error;
            throw new RuntimeException(error);
        }
    }

    private static int workerCount(int n) {
        int available = Math.min(Runtime.getRuntime().availableProcessors(),
                                 ForkJoinPool.getCommonPoolParallelism() + 1);
        int cap = n >= 64_000_000 ? 8 : n >= 4_000_000 ? 6 : 4;
        return Math.max(1, Math.min(available, cap));
    }

    /**
     * Parallel 26-bit bucket distribution with sparse-duplicate reconstruction.
     *
     * Unlike a conventional counting array, we don't update a per-value
     * counter for every input. The occupancy bitmap handles first occurrences;
     * the duplicate counter is touched only for repeated values.
     *
     * This benefits inputs with many distinct values. It still supports
     * arbitrarily many duplicates (up to the int[] length limit).
     */
    private static boolean parallelBucket26(int[] array, int base, int xor,
                                            int first, int verifyMode) {
        int workers = workerCount(array.length);
        if (workers < 2) return radix13(array, base, xor, first, verifyMode);
        final int n = array.length;
        final int[] positions = new int[workers * BUCKET_HIST_STRIDE];
        final int[] mins = new int[workers];
        final int[] maxs = new int[workers];

        parallelFor(workers, w -> {
            int from = (int) ((long) n * w / workers);
            int to = (int) ((long) n * (w + 1) / workers);
            int vmin = Integer.MAX_VALUE, vmax = Integer.MIN_VALUE;
            int offset = w * BUCKET_HIST_STRIDE;
            for (int i = from; i < to; i++) {
                int v = array[i];
                if (v < vmin) vmin = v;
                if (v > vmax) vmax = v;
                positions[offset + ((((v - base) ^ xor) >>> 16) & BUCKET_HIGH_MASK)]++;
            }
            mins[w] = vmin;
            maxs[w] = vmax;
        });

        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int w = 0; w < workers; w++) {
            if (mins[w] < min) min = mins[w];
            if (maxs[w] > max) max = maxs[w];
        }
        if (verifyMode == 1) {
            if ((min >>> 26) != (first >>> 26) ||
                (max >>> 26) != (first >>> 26)) return false;
        } else if ((long) min - base < 0 ||
                   (long) max - base >= (1L << 26)) {
            return false;
        }

        int[] starts = new int[BUCKET_HIGH_BINS + 1];
        int total = 0;
        for (int digit = 0; digit < BUCKET_HIGH_BINS; digit++) {
            starts[digit] = total;
            for (int w = 0; w < workers; w++) {
                int index = w * BUCKET_HIST_STRIDE + digit;
                int count = positions[index];
                positions[index] = total;
                total += count;
            }
        }
        starts[BUCKET_HIGH_BINS] = n;
        // All high 10 bits are already encoded by the destination bucket.
        // Store only the low 16 bits: half the scratch memory and bandwidth.
        char[] temp = borrowBucketScratch(n);
        try {

            parallelFor(workers, w -> {
                int from = (int) ((long) n * w / workers);
                int to = (int) ((long) n * (w + 1) / workers);
                int offset = w * BUCKET_HIST_STRIDE;
                for (int i = from; i < to; i++) {
                    int v = array[i];
                    int key = (v - base) ^ xor;
                    int high = (key >>> 16) & BUCKET_HIGH_MASK;
                    temp[positions[offset + high]++] = (char) key;
                }
            });

            int[] binCuts = new int[workers + 1];
            for (int w = 1; w < workers; w++) {
                int target = (int) ((long) n * w / workers);
                int b = binCuts[w - 1];
                while (b < BUCKET_HIGH_BINS && starts[b] < target) b++;
                binCuts[w] = b;
            }
            binCuts[workers] = BUCKET_HIGH_BINS;

            parallelFor(workers, w -> {
                // Each worker reuses its own small arrays across buckets.
                // Compact 16-bit duplicate counters: half the per-worker footprint.
                // Counts above 65,535 are tracked exactly in the lazily allocated overflow table.
                final char[] duplicateCounts = new char[1 << 16];
                int[] overflowCounts = null;
                final long[] occupied = new long[1 << 10];

                for (int digit = binCuts[w]; digit < binCuts[w + 1]; digit++) {
                    int begin = starts[digit];
                    int end = starts[digit + 1];
                    if (begin == end) continue;
                    Arrays.fill(occupied, 0L);

                    for (int i = begin; i < end; i++) {
                        int low = temp[i];
                        int word = low >>> 6;
                        long mask = 1L << low;
                        long bits = occupied[word];
                        if ((bits & mask) == 0) {
                            occupied[word] = bits | mask;
                        } else {
                            int old = duplicateCounts[low];
                            if (old != 65535) duplicateCounts[low] = (char) (old + 1);
                            else {
                                if (overflowCounts == null) overflowCounts = new int[1 << 16];
                                overflowCounts[low]++;
                            }
                        }
                    }

                    int write = begin;
                    for (int word = 0; word < occupied.length; word++) {
                        long bits = occupied[word];
                        while (bits != 0) {
                            int low = (word << 6) + Long.numberOfTrailingZeros(bits);
                            int duplicates = duplicateCounts[low];
                            if (overflowCounts != null) duplicates += overflowCounts[low];
                            int value = (((digit << 16) | low) ^ xor) + base;
                            if (duplicates == 0) {
                                array[write++] = value;
                            } else {
                                // Reset only counters actually used by duplicates.
                                duplicateCounts[low] = 0;
                                if (overflowCounts != null) overflowCounts[low] = 0;
                                int count = duplicates + 1;
                                if (count >= 8) {
                                    Arrays.fill(array, write, write + count, value);
                                    write += count;
                                } else {
                                    while (count-- > 0) array[write++] = value;
                                }
                            }
                            bits &= bits - 1;
                        }
                    }
                }
            });
            return true;
        } finally {
            recycleBucketScratch(temp);
        }
    }

    /**
     * Detects an already ordered array without a separate sampling pass.
     * Random inputs typically exit after only a handful of comparisons.
     * Equal values and the entire signed int range are supported.
     */
    private static boolean finishIfMonotonic(int[] array, boolean ascending) {
        int previous = array[0];
        int direction = 0;
        int i = 1;

        // Find the first unequal pair; repeated equal values are harmless.
        for (; i < array.length; i++) {
            int current = array[i];
            if (current != previous) {
                direction = current > previous ? 1 : -1;
                previous = current;
                i++;
                break;
            }
        }
        if (direction == 0) return true; // Every value was equal.

        // Verify only the observed direction, quitting at its first violation.
        if (direction > 0) {
            for (; i < array.length; i++) {
                int current = array[i];
                if (current < previous) {
                    return tryRepairNearlySorted(array, i - 1, direction, ascending);
                }
                previous = current;
            }
        } else {
            for (; i < array.length; i++) {
                int current = array[i];
                if (current > previous) {
                    return tryRepairNearlySorted(array, i - 1, direction, ascending);
                }
                previous = current;
            }
        }

        if ((direction > 0) != ascending) reverse(array);
        return true;
    }

    /**
     * Run-aware repair with no fixed limit on the number of edited values.
     *
     * Preserve sorted runs and repair only overlapping displacement intervals.
     * Disjoint untouched runs are not copied, shifted, or sorted.
     *
     * Extra memory is O(k), with k being the number of displaced values,
     * not the input length. If the input is genuinely disordered, we switch
     * to the original bucket/radix path instead of doing excessive repairs.
     */
    private static boolean tryRepairNearlySorted(int[] a, int firstBreak,
                                                  int observedDirection,
                                                  boolean ascending) {
        final int n = a.length;
        int direction = observedDirection;

        // A changed score at the very beginning can make the first pair point
        // in the opposite direction. Infer the prevailing order from a sample.
        if (firstBreak < 1024) {
            int rises = 0, falls = 0;
            int limit = Math.min(n - 1, 1024);
            for (int i = 0; i < limit; i++) {
                if (a[i] < a[i + 1]) rises++;
                else if (a[i] > a[i + 1]) falls++;
            }
            if (rises > falls + 4) direction = 1;
            else if (falls > rises + 4) direction = -1;
        }

        // The prefix before firstBreak is known to be sorted in the observed
        // direction; start near the violation rather than scanning it twice.
        int from = direction == observedDirection ? firstBreak : 0;
        int last = a[from];
        int lastIndex = from;
        int beforeLast = from > 0 ? a[from - 1] : 0;
        boolean haveBefore = from > 0;
        int[] excluded = new int[16];
        int count = 0;

        for (int i = from + 1; i < n; i++) {
            int current = a[i];
            if (ordered(last, current, direction)) {
                beforeLast = last;
                haveBefore = true;
                last = current;
                lastIndex = i;
                continue;
            }

            // When the previous kept item can be removed without breaking the
            // ordered prefix, use one-element lookahead to choose the better
            // candidate. Otherwise discard the current outlier.
            boolean dropPrevious = !haveBefore || ordered(beforeLast, current, direction);
            if (dropPrevious && i + 1 < n) {
                // If the following value already fits after 'last', the
                // current value is the more likely displaced element.
                if (ordered(last, a[i + 1], direction)) dropPrevious = false;
            }

            if (count == excluded.length) {
                excluded = Arrays.copyOf(excluded, excluded.length << 1);
            }
            if (dropPrevious) {
                excluded[count++] = lastIndex;
                // The previous kept value was 'beforeLast'. It remains kept.
                last = current;
                lastIndex = i;
            } else {
                excluded[count++] = i;
            }

            // This is a *density* threshold, not a limit on the number of
            // changes. Large sparse edits still take the incremental path;
            // random data falls back quickly to the bulk sorter.
            // Genuinely random input violates order very densely. Reject it
            // after a tiny prefix rather than scanning 16K+ values first.
            // This is a density heuristic, not a fixed edit-count limit.
            if ((i - from >= 256 && (long) count * 8 > i - from)
                    || (i >= 16_384 && count > (i >>> 5))) return false;
        }

        if (count == 0) {
            if ((direction > 0) != ascending) reverse(a);
            return true;
        }
        if (count == 1) {
            int index = excluded[0];
            int target = insertionIndexExcluding(a, index, direction);
            int value = a[index];
            if (target < index) {
                System.arraycopy(a, target, a, target + 1, index - target);
            } else if (target > index) {
                System.arraycopy(a, index + 1, a, index, target - index);
            }
            a[target] = value;
            if ((direction > 0) != ascending) reverse(a);
            return true;
        }

        // A previous kept element can be discarded after newer elements,
        // so normalize the candidate indices before compacting the runs.
        Arrays.sort(excluded, 0, count);

        // Treat excluded values as the only unsorted elements. All remaining
        // elements form a monotonic virtual array. We can binary-search that
        // array WITHOUT physically compacting the original 5M entries.
        //
        // For sparse edits, find disjoint affected intervals and repair only
        // those intervals. An untouched run between edits is never copied.
        // An O(n) global merge remains available for dense-but-repairable input.
        if ((long) count * (32 - Integer.numberOfLeadingZeros(n))
                * (32 - Integer.numberOfLeadingZeros(count)) > (long) n * 2) {
            mergeAllExcluded(a, excluded, count, direction);
        } else {
            repairDisjointIntervals(a, excluded, count, direction);
        }

        if ((direction > 0) != ascending) reverse(a);
        return true;
    }

    /**
     * Build the final insertion positions while the input is intact.
     * Every moved element defines an interval between its original and final
     * positions. Overlapping intervals are repaired together; all untouched
     * positions remain unchanged. No array proportional to n is allocated.
     */
    private static void repairDisjointIntervals(int[] a, int[] excluded,
                                                int count, int direction) {
        final int n = a.length;
        final int kept = n - count;
        long[] extras = new long[count];
        for (int j = 0; j < count; j++) {
            int idx = excluded[j];
            extras[j] = ((long) a[idx] << 32) | (idx & 0xffffffffL);
        }
        Arrays.sort(extras);
        if (direction < 0) {
            for (int i = 0, j = count - 1; i < j; i++, j--) {
                long tmp = extras[i]; extras[i] = extras[j]; extras[j] = tmp;
            }
        }

        int[] destinations = new int[count];
        long[] intervals = new long[count];
        for (int j = 0; j < count; j++) {
            int value = (int) (extras[j] >> 32);
            int position = insertionInKept(a, excluded, count, kept, value, direction);
            int destination = position + j;
            int original = (int) extras[j];
            destinations[j] = destination;
            int low = Math.min(original, destination);
            int high = Math.max(original, destination);
            intervals[j] = ((long) low << 32) | (high & 0xffffffffL);
        }
        Arrays.sort(intervals);

        int originalCursor = 0;
        int extraCursor = 0;
        for (int j = 0; j < count;) {
            int low = (int) (intervals[j] >>> 32);
            int high = (int) intervals[j];
            j++;
            // Touching intervals can be combined into one sequential copy.
            while (j < count && (int) (intervals[j] >>> 32) <= high + 1) {
                high = Math.max(high, (int) intervals[j]);
                j++;
            }

            int originalFrom = originalCursor;
            while (originalCursor < count && excluded[originalCursor] <= high)
                originalCursor++;
            int extraFrom = extraCursor;
            while (extraCursor < count && destinations[extraCursor] <= high)
                extraCursor++;

            // Both counts must be equal for a closed interval. This follows
            // from pairing each removed item with its final output index.
            // The check is defensive; the normal path never needs a fallback.
            if (originalCursor - originalFrom != extraCursor - extraFrom)
                throw new AssertionError("Unbalanced repair interval");

            repairInterval(a, low, high, excluded, originalFrom, originalCursor,
                           extras, extraFrom, extraCursor, direction);
        }
    }

    // Select a value from the virtual sorted array with excluded indices removed.
    private static int keptSourceIndex(int virtualIndex, int[] excluded, int count) {
        int low = 0, high = count;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (excluded[mid] - mid <= virtualIndex) low = mid + 1;
            else high = mid;
        }
        return virtualIndex + low;
    }

    private static int insertionInKept(int[] a, int[] excluded, int count,
                                       int kept, int value, int direction) {
        int low = 0, high = kept;
        while (low < high) {
            int mid = (low + high) >>> 1;
            int candidate = a[keptSourceIndex(mid, excluded, count)];
            if (direction > 0 ? candidate < value : candidate > value)
                low = mid + 1;
            else high = mid;
        }
        return low;
    }

    /** Compact and merge only one affected region; constant working memory. */
    private static void repairInterval(int[] a, int low, int high,
                                       int[] excluded, int eFrom, int eTo,
                                       long[] extras, int xFrom, int xTo,
                                       int direction) {
        int read = low, write = low;
        for (int j = eFrom; j < eTo; j++) {
            int index = excluded[j];
            int len = index - read;
            if (len != 0) {
                System.arraycopy(a, read, a, write, len);
                write += len;
            }
            read = index + 1;
        }
        if (read <= high) {
            int len = high + 1 - read;
            if (read != write) System.arraycopy(a, read, a, write, len);
            write += len;
        }

        int kept = write - 1;
        int extra = xTo - 1;
        for (int dst = high; dst >= low && extra >= xFrom; dst--) {
            if (kept < low) {
                a[dst] = (int) (extras[extra--] >> 32);
            } else {
                int stored = a[kept];
                int candidate = (int) (extras[extra] >> 32);
                if (direction > 0 ? stored >= candidate : stored <= candidate) {
                    a[dst] = stored;
                    kept--;
                } else {
                    a[dst] = candidate;
                    extra--;
                }
            }
        }
    }

    /** Large sparse repairs: one full linear compaction and backward merge. */
    private static void mergeAllExcluded(int[] a, int[] excluded,
                                         int count, int direction) {
        final int n = a.length;
        int[] extras = new int[count];
        int read = 0, write = 0;
        for (int j = 0; j < count; j++) {
            int removed = excluded[j];
            extras[j] = a[removed];
            int len = removed - read;
            if (len > 0) System.arraycopy(a, read, a, write, len);
            write += len;
            read = removed + 1;
        }
        if (read < n) System.arraycopy(a, read, a, write, n - read);
        Arrays.sort(extras);
        if (direction < 0) {
            for (int l = 0, r = count - 1; l < r; l++, r--) {
                int t = extras[l]; extras[l] = extras[r]; extras[r] = t;
            }
        }
        int keep = n - count - 1;
        int extra = count - 1;
        for (int dst = n - 1; dst >= 0 && extra >= 0; dst--) {
            if (keep < 0) a[dst] = extras[extra--];
            else if (direction > 0 ? a[keep] >= extras[extra] : a[keep] <= extras[extra])
                a[dst] = a[keep--];
            else a[dst] = extras[extra--];
        }
    }

    private static boolean ordered(int left, int right, int direction) {
        return direction > 0 ? left <= right : left >= right;
    }

    private static int insertionIndexExcluding(int[] a, int excluded, int direction) {
        int value = a[excluded];
        int lo = 0, hi = a.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            int current = a[mid < excluded ? mid : mid + 1];
            boolean before = direction > 0 ? current < value : current > value;
            if (before) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private static void reverse(int[] array) {
        for (int l = 0, r = array.length - 1; l < r; l++, r--) {
            int x = array[l]; array[l] = array[r]; array[r] = x;
        }
    }

    // =====================================================================
    // Object and Map API (Java 8)
    // =====================================================================
    // Weight extractors must be deterministic during a sort. They are called
    // ONCE per item; numeric keys are cached, not recomputed during passes.
    // Equal weights retain input order (stable); hashCode is not natural order.

    /** Default primitive order is ascending. */
    public static void sort(int[] array) {
        sortAscending(array);
    }

    public static <T> void sort(T[] array) {
        sortByWeight(array, new ToIntFunction<T>() {
            public int applyAsInt(T object) { return Objects.hashCode(object); }
        }, true);
    }

    public static <T> void sort(T[] array, ToIntFunction<? super T> weight) {
        sortByWeight(array, weight, true);
    }

    public static <T> void sort(T[] array, ToIntFunction<? super T> weight, boolean ascending) {
        sortByWeight(array, weight, ascending);
    }

    public static <T> void sortByWeight(T[] array, ToIntFunction<? super T> weight) {
        sortByWeight(array, weight, true);
    }

    public static <T> void sortByWeight(T[] array, ToIntFunction<? super T> weight,
                                        boolean ascending) {
        Objects.requireNonNull(array, "array");
        Objects.requireNonNull(weight, "weight");
        objectWeightInt(array, weight, ascending);
    }

    public static <T> void sortByLongWeight(T[] array, ToLongFunction<? super T> weight,
                                            boolean ascending) {
        Objects.requireNonNull(array, "array");
        Objects.requireNonNull(weight, "weight");
        objectWeightLong(array, weight, ascending);
    }

    /** Arbitrary Comparators cannot generally be translated into numeric weights. */
    public static <T> void sort(T[] array, Comparator<? super T> comparator) {
        Objects.requireNonNull(array, "array");
        Objects.requireNonNull(comparator, "comparator");
        comparatorMergeSort(array, comparator);
    }

    public static void sort(Number[] numbers) { sort(numbers, true); }

    public static void sortAscending(Number[] numbers) { sort(numbers, true); }

    public static void sortDescending(Number[] numbers) { sort(numbers, false); }

    /** Numeric sort (not hash order). Nulls last, in either direction. */
    public static void sort(Number[] numbers, boolean ascending) {
        Objects.requireNonNull(numbers, "numbers");
        if (numbers.length < 2) return;
        boolean ints = true, longs = true, floats = true, doubles = true;
        for (Number x : numbers) {
            if (x == null) { ints = longs = floats = doubles = false; break; }
            if (!(x instanceof Integer || x instanceof Short || x instanceof Byte)) ints = false;
            if (!(x instanceof Long)) longs = false;
            if (!(x instanceof Float)) floats = false;
            if (!(x instanceof Double)) doubles = false;
        }
        if (ints) objectWeightInt(numbers, x -> x.intValue(), ascending);
        else if (longs) objectWeightLong(numbers, x -> x.longValue(), ascending);
        else if (floats) objectWeightInt(numbers, x -> floatWeight(x.floatValue()), ascending);
        else if (doubles) objectWeightLong(numbers, x -> doubleWeight(x.doubleValue()), ascending);
        else comparatorMergeSort(numbers, (a, b) -> compareNumber(a, b, ascending));
    }

    public static <K, V> LinkedHashMap<K, V> sortByKey(Map<K, V> map) {
        return sortByKeyWeight(map, Objects::hashCode, true);
    }

    /** Numeric values sort by their value, nonnumeric values by hashCode. */
    public static <K, V> LinkedHashMap<K, V> sortByValue(Map<K, V> map) {
        return sortByValue(map, true);
    }

    public static <K, V> LinkedHashMap<K, V> sortByValue(Map<K, V> map, boolean ascending) {
        Map.Entry<K,V>[] es = copyEntries(map);
        sortMapValuesAuto(es, ascending);
        return linked(es);
    }

    public static <K,V> LinkedHashMap<K,V> sortByKey(
            Map<K,V> map, ToIntFunction<? super K> weight) {
        return sortByKeyWeight(map, weight, true);
    }

    public static <K,V> LinkedHashMap<K,V> sortByValue(
            Map<K,V> map, ToIntFunction<? super V> weight) {
        return sortByValueWeight(map, weight, true);
    }

    public static <K,V> LinkedHashMap<K,V> sortByKey(
            Map<K,V> map, ToIntFunction<? super K> weight, boolean ascending) {
        return sortByKeyWeight(map, weight, ascending);
    }

    public static <K,V> LinkedHashMap<K,V> sortByValue(
            Map<K,V> map, ToIntFunction<? super V> weight, boolean ascending) {
        return sortByValueWeight(map, weight, ascending);
    }

    public static <K,V> LinkedHashMap<K,V> sortByKeyWeight(
            Map<K,V> map, ToIntFunction<? super K> weight) {
        return sortByKeyWeight(map, weight, true);
    }

    public static <K,V> LinkedHashMap<K,V> sortByValueWeight(
            Map<K,V> map, ToIntFunction<? super V> weight) {
        return sortByValueWeight(map, weight, true);
    }

    public static <K,V> LinkedHashMap<K,V> sortByKeyWeight(
            Map<K,V> map, ToIntFunction<? super K> weight, boolean ascending) {
        Objects.requireNonNull(weight, "weight");
        Map.Entry<K,V>[] es = copyEntries(map);
        objectWeightInt(es, e -> weight.applyAsInt(e.getKey()), ascending);
        return linked(es);
    }

    public static <K,V> LinkedHashMap<K,V> sortByValueWeight(
            Map<K,V> map, ToIntFunction<? super V> weight, boolean ascending) {
        Objects.requireNonNull(weight, "weight");
        Map.Entry<K,V>[] es = copyEntries(map);
        objectWeightInt(es, e -> weight.applyAsInt(e.getValue()), ascending);
        return linked(es);
    }

    public static <K,V> LinkedHashMap<K,V> sortByKeyLongWeight(
            Map<K,V> map, ToLongFunction<? super K> weight, boolean ascending) {
        Objects.requireNonNull(weight, "weight");
        Map.Entry<K,V>[] es = copyEntries(map);
        objectWeightLong(es, e -> weight.applyAsLong(e.getKey()), ascending);
        return linked(es);
    }

    public static <K,V> LinkedHashMap<K,V> sortByValueLongWeight(
            Map<K,V> map, ToLongFunction<? super V> weight, boolean ascending) {
        Objects.requireNonNull(weight, "weight");
        Map.Entry<K,V>[] es = copyEntries(map);
        objectWeightLong(es, e -> weight.applyAsLong(e.getValue()), ascending);
        return linked(es);
    }

    public static <K,V> LinkedHashMap<K,V> sortByKey(
            Map<K,V> map, Comparator<? super K> comparator) {
        Objects.requireNonNull(comparator, "comparator");
        Map.Entry<K,V>[] es = copyEntries(map);
        comparatorMergeSort(es, (a,b) -> comparator.compare(a.getKey(), b.getKey()));
        return linked(es);
    }

    public static <K,V> LinkedHashMap<K,V> sortByValue(
            Map<K,V> map, Comparator<? super V> comparator) {
        Objects.requireNonNull(comparator, "comparator");
        Map.Entry<K,V>[] es = copyEntries(map);
        comparatorMergeSort(es, (a,b) -> comparator.compare(a.getValue(), b.getValue()));
        return linked(es);
    }

    /** More memory-efficient than building another LinkedHashMap. */
    public static <K,V> List<Map.Entry<K,V>> entriesByValueWeight(
            Map<K,V> map, ToIntFunction<? super V> weight, boolean ascending) {
        Objects.requireNonNull(weight, "weight");
        Map.Entry<K,V>[] es = copyEntries(map);
        objectWeightInt(es, e -> weight.applyAsInt(e.getValue()), ascending);
        return Arrays.asList(es);
    }

    public static <K,V> List<Map.Entry<K,V>> entriesByKeyWeight(
            Map<K,V> map, ToIntFunction<? super K> weight, boolean ascending) {
        Objects.requireNonNull(weight, "weight");
        Map.Entry<K,V>[] es = copyEntries(map);
        objectWeightInt(es, e -> weight.applyAsInt(e.getKey()), ascending);
        return Arrays.asList(es);
    }

    public static <K,V> List<Map.Entry<K,V>> entriesByValue(
            Map<K,V> map, boolean ascending) {
        Map.Entry<K,V>[] es = copyEntries(map);
        sortMapValuesAuto(es, ascending);
        return Arrays.asList(es);
    }

    @SuppressWarnings("unchecked")
    private static <K,V> Map.Entry<K,V>[] copyEntries(Map<K,V> map) {
        Objects.requireNonNull(map, "map");
        return map.entrySet().toArray(new Map.Entry[map.size()]);
    }

    private static <K,V> LinkedHashMap<K,V> linked(Map.Entry<K,V>[] es) {
        // Keep capacity arithmetic safe for huge maps.
        int capacity = (int) Math.min(1_073_741_824L,
                Math.max(16L, ((long) es.length * 4L / 3L) + 1L));
        LinkedHashMap<K,V> result = new LinkedHashMap<K,V>(capacity);
        for (Map.Entry<K,V> e : es) result.put(e.getKey(), e.getValue());
        return result;
    }

    private static <K,V> void sortMapValuesAuto(Map.Entry<K,V>[] es,
                                                  boolean ascending) {
        if (es.length == 0) return;
        boolean number = true, ints = true, longs = true, floats = true, doubles = true;
        for (Map.Entry<K,V> e : es) {
            Object v = e.getValue();
            if (v == null) {
                ints = longs = floats = doubles = false;
                continue;
            }
            if (!(v instanceof Number)) { number = ints = longs = floats = doubles = false; break; }
            if (!(v instanceof Integer || v instanceof Short || v instanceof Byte)) ints = false;
            if (!(v instanceof Long)) longs = false;
            if (!(v instanceof Float)) floats = false;
            if (!(v instanceof Double)) doubles = false;
        }
        if (ints) objectWeightInt(es, e -> ((Number)e.getValue()).intValue(), ascending);
        else if (longs) objectWeightLong(es, e -> ((Number)e.getValue()).longValue(), ascending);
        else if (floats) objectWeightInt(es, e -> floatWeight(((Number)e.getValue()).floatValue()), ascending);
        else if (doubles) objectWeightLong(es, e -> doubleWeight(((Number)e.getValue()).doubleValue()), ascending);
        else if (number) comparatorMergeSort(es, (a,b) -> compareNumber((Number)a.getValue(), (Number)b.getValue(), ascending));
        else objectWeightInt(es, e -> Objects.hashCode(e.getValue()), ascending);
    }

    // Stable parallel redistribution of object references using cached weights.
    // Mirrors the primitive engine's bucket histogram/prefix/scatter stages.
    // Unlike int[] values, objects cannot be reconstructed from occupancy bits.
    // 1 temp reference array + 2 int key arrays. No per-item wrappers.
    private static <T> void objectWeightInt(T[] a, ToIntFunction<? super T> weight,
                                             boolean ascending) {
        final int n = a.length;
        if (n < 2) return;
        final int flip = ascending ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        final int[] key = new int[n];
        boolean sorted = true;
        int variation = 0;
        int prev = 0;
        for (int i = 0; i < n; ++i) {
            int k = weight.applyAsInt(a[i]) ^ flip;
            key[i] = k;
            if (i > 0) { if (Integer.compareUnsigned(prev, k) > 0) sorted = false;
                variation |= prev ^ k; }
            prev = k;
        }
        if (sorted || variation == 0) return;
        if (n <= 64) {
            for (int i = 1; i < n; i++) {
                T item = a[i];
                int k = key[i], j = i - 1;
                while (j >= 0 && Integer.compareUnsigned(key[j], k) > 0) {
                    a[j + 1] = a[j]; key[j + 1] = key[j]; --j;
                }
                a[j + 1] = item; key[j + 1] = k;
            }
            return;
        }
        final boolean onePass = (variation >>> 16) == 0;
        final int bins = 1 << 16;
        final int mask = bins - 1;
        final int workers = objectWorkers(n);
        final int[][] position = new int[workers][bins];
        objectParallel(workers, w -> {
            int from = (int)((long)n*w/workers), to = (int)((long)n*(w+1)/workers);
            int[] h = position[w];
            for (int i = from; i < to; ++i) ++h[key[i] & mask];
        });
        objectPrefix(position, workers, bins);
        final Object[] scratch = new Object[n];
        final int[] scratchKeys = onePass ? null : new int[n];
        objectParallel(workers, w -> {
            int from = (int)((long)n*w/workers), to = (int)((long)n*(w+1)/workers);
            int[] h = position[w];
            for (int i = from; i < to; ++i) {
                int k = key[i];
                int dest = h[k & mask]++;
                scratch[dest] = a[i];
                if (scratchKeys != null) scratchKeys[dest] = k;
            }
        });
        if (onePass) {
            System.arraycopy(scratch, 0, a, 0, n);
            return;
        }
        final int[][] high = new int[workers][bins];
        objectParallel(workers, w -> {
            int from = (int)((long)n*w/workers), to = (int)((long)n*(w+1)/workers);
            int[] h = high[w];
            for (int i = from; i < to; ++i) ++h[scratchKeys[i] >>> 16];
        });
        objectPrefix(high, workers, bins);
        objectParallel(workers, w -> {
            int from = (int)((long)n*w/workers), to = (int)((long)n*(w+1)/workers);
            int[] h = high[w];
            for (int i = from; i < to; ++i) {
                int dest = h[scratchKeys[i] >>> 16]++;
                a[dest] = (T) scratch[i];
            }
        });
    }

    private static <T> void objectWeightLong(T[] a, ToLongFunction<? super T> weight,
                                               boolean ascending) {
        int n = a.length;
        if (n < 2) return;
        final long flip = ascending ? Long.MIN_VALUE : Long.MAX_VALUE;
        final long[] key = new long[n];
        boolean sorted = true;
        long prev = 0;
        long variation = 0;
        for (int i = 0; i < n; ++i) {
            long k = weight.applyAsLong(a[i]) ^ flip;
            key[i] = k;
            if (i > 0) { if (Long.compareUnsigned(prev, k) > 0) sorted = false;
                variation |= prev ^ k; }
            prev = k;
        }
        if (sorted || variation == 0) return;
        if (n <= 64) {
            for (int i = 1; i < n; i++) {
                T item = a[i];
                long k = key[i]; int j = i - 1;
                while (j >= 0 && Long.compareUnsigned(key[j], k) > 0) {
                    a[j + 1] = a[j]; key[j + 1] = key[j]; --j;
                }
                a[j + 1] = item; key[j + 1] = k;
            }
            return;
        }
        // LSD passes are skipped if no values differ in those 16-bit digits.
        int[] shifts = new int[4];
        int count = 0;
        for (int shift = 0; shift < 64; shift += 16)
            if (((variation >>> shift) & 0xffffL) != 0) shifts[count++] = shift;
        final int nworkers = objectWorkers(n);
        final int bins = 1 << 16;
        Object[] buffer = new Object[n];
        long[] keyBuffer = new long[n];
        Object[] src = a, dst = buffer;
        long[] keySrc = key, keyDst = keyBuffer;
        for (int run = 0; run < count; ++run) {
            final int shift = shifts[run];
            final Object[] fromArray = src, toArray = dst;
            final long[] fromKeys = keySrc, toKeys = keyDst;
            final int[][] positions = new int[nworkers][bins];
            objectParallel(nworkers, w -> {
                int from = (int)((long)n*w/nworkers), to = (int)((long)n*(w+1)/nworkers);
                int[] h = positions[w];
                for (int i = from; i < to; i++) h[(int)((fromKeys[i] >>> shift) & 0xffffL)]++;
            });
            objectPrefix(positions, nworkers, bins);
            objectParallel(nworkers, w -> {
                int from = (int)((long)n*w/nworkers), to = (int)((long)n*(w+1)/nworkers);
                int[] h = positions[w];
                for (int i = from; i < to; ++i) {
                    long k = fromKeys[i];
                    int dest = h[(int)((k >>> shift) & 0xffffL)]++;
                    toArray[dest] = fromArray[i];
                    toKeys[dest] = k;
                }
            });
            Object[] tt = src; src = dst; dst = tt;
            long[] kk = keySrc; keySrc = keyDst; keyDst = kk;
        }
        if (src != a) System.arraycopy(src, 0, a, 0, n);
    }

    private static void objectPrefix(int[][] pos, int workers, int bins) {
        int start = 0;
        for (int digit = 0; digit < bins; ++digit) {
            for (int w = 0; w < workers; ++w) {
                int amount = pos[w][digit];
                pos[w][digit] = start;
                start += amount;
            }
        }
    }

    private static int objectWorkers(int n) {
        if (n < 120_000) return 1;
        return Math.max(1, Math.min(6, Math.min(Runtime.getRuntime().availableProcessors(),
                ForkJoinPool.getCommonPoolParallelism() + 1)));
    }

    private static void objectParallel(int workers, IntConsumer job) {
        if (workers == 1) { job.accept(0); return; }
        // Reuse the existing core's safe fork/join job coordination.
        parallelFor(workers, job);
    }

    // Stable bottom-up merge sort with run optimization for arbitrary Comparator.
    // Comparator sorting is O(n log n) and cannot use weight scatter generally.
    @SuppressWarnings("unchecked")
    private static <T> void comparatorMergeSort(T[] a, Comparator<? super T> c) {
        int n = a.length;
        if (n < 2) return;
        boolean ordered = true;
        for (int i = 1; i < n; i++) {
            if (c.compare(a[i-1], a[i]) > 0) { ordered = false; break; }
        }
        if (ordered) return;
        for (int begin = 0; begin < n; begin += 32) {
            int end = Math.min(n, begin + 32);
            for (int i = begin + 1; i < end; i++) {
                T current = a[i]; int j = i-1;
                while (j >= begin && c.compare(a[j], current) > 0) {
                    a[j+1] = a[j]; j--;
                }
                a[j+1] = current;
            }
        }
        Object[] buffer = new Object[n];
        Object[] src = a, dst = buffer;
        for (long width = 32; width < n; width <<= 1) {
            long step = width << 1;
            for (long start = 0; start < n; start += step) {
                int l = (int)start, m = (int)Math.min(n, start + width), r = (int)Math.min(n, start + step);
                if (m == r || c.compare((T)src[m-1], (T)src[m]) <= 0) {
                    System.arraycopy(src,l,dst,l,r-l); continue;
                }
                int i=l,j=m,k=l;
                while (i<m && j<r) {
                    if (c.compare((T)src[i],(T)src[j]) <= 0) dst[k++]=src[i++];
                    else dst[k++]=src[j++];
                }
                if (i<m) System.arraycopy(src,i,dst,k,m-i);
                else if (j<r) System.arraycopy(src,j,dst,k,r-j);
            }
            Object[] tmp=src; src=dst; dst=tmp;
        }
        if (src != a) System.arraycopy(src,0,a,0,n);
    }

    private static int floatWeight(float x) {
        int bits = Float.floatToIntBits(x);
        int sortable = bits < 0 ? ~bits : bits ^ Integer.MIN_VALUE;
        return sortable ^ Integer.MIN_VALUE;
    }

    private static long doubleWeight(double x) {
        long bits = Double.doubleToLongBits(x);
        long sortable = bits < 0 ? ~bits : bits ^ Long.MIN_VALUE;
        return sortable ^ Long.MIN_VALUE;
    }

    private static int compareNumber(Number a, Number b, boolean ascending) {
        if (a == b) return 0;
        // nulls last for both ascending and descending.
        if (a == null) return 1;
        if (b == null) return -1;
        int ca = numericClass(a), cb = numericClass(b);
        int result;
        if (ca != 0 || cb != 0) result = Integer.compare(ca, cb);
        else {
            result = decimal(a).compareTo(decimal(b));
            if (result == 0 && a.doubleValue() == 0.0 && b.doubleValue() == 0.0)
                result = Boolean.compare(negativeZero(b), negativeZero(a));
        }
        return ascending ? result : -result;
    }

    private static int numericClass(Number n) {
        if (n instanceof Double) {
            double d = n.doubleValue();
            if (Double.isNaN(d)) return 2;
            if (d == Double.NEGATIVE_INFINITY) return -1;
            if (d == Double.POSITIVE_INFINITY) return 1;
        } else if (n instanceof Float) {
            float f = n.floatValue();
            if (Float.isNaN(f)) return 2;
            if (f == Float.NEGATIVE_INFINITY) return -1;
            if (f == Float.POSITIVE_INFINITY) return 1;
        }
        return 0;
    }

    private static BigDecimal decimal(Number n) {
        if (n instanceof BigDecimal) return (BigDecimal)n;
        if (n instanceof BigInteger) return new BigDecimal((BigInteger)n);
        if (n instanceof Byte || n instanceof Short || n instanceof Integer || n instanceof Long)
            return BigDecimal.valueOf(n.longValue());
        if (n instanceof Double) return BigDecimal.valueOf(n.doubleValue());
        if (n instanceof Float) return new BigDecimal(Float.toString(n.floatValue()));
        return new BigDecimal(n.toString());
    }

    private static boolean negativeZero(Number n) {
        if (n instanceof Double) return Double.doubleToRawLongBits(n.doubleValue()) == Long.MIN_VALUE;
        if (n instanceof Float) return Float.floatToRawIntBits(n.floatValue()) == Integer.MIN_VALUE;
        return false;
    }


}
