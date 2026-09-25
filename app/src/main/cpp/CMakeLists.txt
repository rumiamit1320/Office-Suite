cmake_minimum_required(VERSION 3.22.1)
project(docuflow-lokit)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(docuflow-lokit SHARED lokit_bridge.cpp)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(docuflow-lokit PRIVATE
    ${log-lib}
    ${android-lib}
    dl
)
