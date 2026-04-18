/**
 * Native whisper.cpp library packaging and loading.
 *
 * <p>This module builds whisper.cpp from pinned source (v1.8.4) and packages the resulting native
 * library as a classpath resource under {@code native/<platform>/libwhisper.dylib}. The {@link
 * NativeLibraryLoader} extracts the library at runtime and loads it via {@code System.load}.
 */
package com.pairion.nativelib.whisper;
