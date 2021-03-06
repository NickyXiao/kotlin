// !LANGUAGE: +MultiPlatformProjects
// MODULE: m1-common
// FILE: common.kt
header enum class Foo {
    ENTRY1,
    ENTRY2,
    ENTRY3;
}

// MODULE: m2-jvm(m1-common)
// FILE: jvm.kt
impl enum class Foo(val x: String) {
    ENTRY1("1"),
    ENTRY2("2"),
    ENTRY3("3");
}
