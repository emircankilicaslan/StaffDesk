package com.staffdesk.domain;

public enum LeaveType {
    /** Yıllık izin: hak ediş ve bakiye kontrolüne tabidir. */
    ANNUAL,
    /** Hastalık izni: geriye dönük en fazla 30 gün bildirilebilir. */
    SICK,
    /** Mazeret izni. */
    EXCUSE
}
