cmake_minimum_required(VERSION 3.16)
project(abyssme_brew_host LANGUAGES C)

# Original host integration stub. Does not compile or ship any Qualcomm code.
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

add_library(abyss_brew_host STATIC src/abyss_brew_host.c)
target_include_directories(abyss_brew_host PUBLIC
    "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_compile_options(abyss_brew_host PRIVATE
    $<$<C_COMPILER_ID:GNU,Clang>:-Wall>
    $<$<C_COMPILER_ID:GNU,Clang>:-Wextra>
    $<$<C_COMPILER_ID:GNU,Clang>:-Werror>)

include(CTest)
if(BUILD_TESTING AND NOT ANDROID)
    add_executable(abyss_brew_host_smoke tests/host_smoke.c)
    target_link_libraries(abyss_brew_host_smoke PRIVATE abyss_brew_host)
    add_test(NAME abyss_brew_host_smoke COMMAND abyss_brew_host_smoke)
endif()
