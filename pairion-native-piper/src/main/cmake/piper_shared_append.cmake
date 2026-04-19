# ---- piper_shared — C wrapper shared library for JVM/FFM binding ----
# Appended to CMakeLists.txt by the pairion-native-piper Maven build.
# Builds libpiper.dylib / libpiper.so from piper.cpp + piper_c.cpp so that
# the Java FFM layer can load it via System.load() and call the C wrapper API.

add_library(piper_shared SHARED
    src/cpp/piper.cpp
    src/cpp/piper_c.cpp
)

set_target_properties(piper_shared PROPERTIES
    OUTPUT_NAME "piper"
    MACOSX_RPATH TRUE
    BUILD_RPATH "@loader_path"
    INSTALL_RPATH "@loader_path"
)

# Strip trailing newline from piper_version before using it in SHARED lib defines
# (file(READ) includes the newline, which corrupts generated Makefile rules for SHARED targets)
string(STRIP "${piper_version}" piper_version_stripped)

if(TARGET fmt_external)
    add_dependencies(piper_shared fmt_external)
endif()
if(TARGET spdlog_external)
    add_dependencies(piper_shared spdlog_external)
endif()
if(TARGET piper_phonemize_external)
    add_dependencies(piper_shared piper_phonemize_external)
endif()

target_link_libraries(piper_shared PUBLIC
    fmt
    spdlog
    espeak-ng
    piper_phonemize
    onnxruntime
)

target_link_directories(piper_shared PUBLIC
    ${FMT_DIR}/lib
    ${SPDLOG_DIR}/lib
    ${PIPER_PHONEMIZE_DIR}/lib
)

target_include_directories(piper_shared PUBLIC
    ${FMT_DIR}/include
    ${SPDLOG_DIR}/include
    ${PIPER_PHONEMIZE_DIR}/include
)

target_compile_definitions(piper_shared PUBLIC
    _PIPER_VERSION=${piper_version_stripped}
)
