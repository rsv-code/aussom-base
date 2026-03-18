package com.aussom.stdlib;

public class UnitTestResult {
    public int total = 0;
    public int skipped = 0;
    public int passed = 0;
    public int failed = 0;

    public UnitTestResult() {}

    public void merge(UnitTestResult res) {
        this.total += res.total;
        this.skipped += res.skipped;
        this.passed += res.passed;
        this.failed += res.failed;
    }
}
