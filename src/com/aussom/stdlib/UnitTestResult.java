package com.aussom.stdlib;

import com.aussom.types.AussomInt;
import com.aussom.types.AussomMap;
import com.aussom.types.AussomType;

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

    public AussomType toAussomType() {
        AussomMap ret = new AussomMap();
        ret.put("total", new AussomInt(this.total));
        ret.put("skipped", new AussomInt(this.skipped));
        ret.put("passed", new AussomInt(this.passed));
        ret.put("failed", new AussomInt(this.failed));
        return ret;
    }
}
