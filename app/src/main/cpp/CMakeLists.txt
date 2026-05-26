cmake_minimum_required(VERSION 3.22.1)

project("qemu_wrapper")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(QEMU_SRC_DIR ${CMAKE_SOURCE_DIR}/qemu)

set(QEMU_SOURCES
    ${QEMU_SRC_DIR}/qemu_wrapper.cpp
    ${QEMU_SRC_DIR}/display.c
    ${QEMU_SRC_DIR}/input.c
)

add_library(qemu_wrapper SHARED ${QEMU_SOURCES})

target_include_directories(qemu_wrapper PRIVATE
    ${CMAKE_SOURCE_DIR}/include
    ${QEMU_SRC_DIR}
)

target_link_libraries(qemu_wrapper
    android
    log
)

set_target_properties(qemu_wrapper PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    C_VISIBILITY_PRESET hidden
)
