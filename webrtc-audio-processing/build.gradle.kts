plugins {
    `java-library`
}

dependencies {
    api(libs.jna)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}
