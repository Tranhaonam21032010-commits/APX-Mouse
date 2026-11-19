cmake_minimum_required(VERSION 3.22.1)
project(apx_input LANGUAGES CXX)

add_library(apx_input SHARED native_input.cpp)
target_compile_features(apx_input PRIVATE cxx_std_17)
target_compile_options(apx_input PRIVATE -Wall -Wextra -Werror=return-type)

# NDK r27 needs an explicit linker page-size setting for 16 KB Android devices.
# This affects ELF PT_LOAD segment alignment in libapx_input.so.
target_link_options(apx_input PRIVATE "-Wl,-z,max-page-size=16384")
