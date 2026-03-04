cmake_minimum_required(VERSION 3.22.1)
project(putivnyk_native)

add_library(
    putivnyk_native
    SHARED
    geo_native.cpp
)

target_link_options(
    putivnyk_native
    PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

find_library(log-lib log)

target_link_libraries(
    putivnyk_native
    ${log-lib}
)
