package com.example.goldentimealarm

class GoldenTimeException(val errorCode: Int) : RuntimeException() {
    companion object {
        val DATE_LINE_PARSE_ERROR = 1001
        val GOLDEN_TIME_REQUEST_FAIL = 1002
        val OLD_GOLDEN_TIME = 1003
        val MAY_BE_HTML_CHANGED = 1004
    }
}