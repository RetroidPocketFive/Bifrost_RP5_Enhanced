package com.moonbench.bifrost.rp5

interface LedDriver {
    fun write(frame: LedFrame)
    fun clear()
}
