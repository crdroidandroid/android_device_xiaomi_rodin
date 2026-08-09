#!/system/bin/sh
# Dynamic zram (swap) size helper for Xiaomi Parts.
# Spawned by init from init.zram_resize.rc in the dedicated zram_resize
# domain (vendor_init is neverallowed to execute programs without
# transitioning to another domain).
# Reads the desired size (percent of total RAM) from
# persist.sys.zram_size_percent and re-creates the zram0 swap device at
# that size (in-RAM, no backing device).
# Debug output goes to /data/local/tmp/zram_resize.log.

LOG=/data/local/tmp/zram_resize.log
log_i() { echo "I $(date +%s): $*" >> "$LOG" 2>/dev/null; }
log_e() { echo "E $(date +%s): $*" >> "$LOG" 2>/dev/null; }

log_i "triggered, pct=$(getprop persist.sys.zram_size_percent)"

PCT=$(getprop persist.sys.zram_size_percent)
case "$PCT" in
    ''|*[!0-9]*) log_e "invalid pct, exiting"; exit 0 ;;
esac

[ "$PCT" -lt 10 ] && PCT=10
[ "$PCT" -gt 80 ] && PCT=80

MEM_TOTAL_KB=$(sed -n 's/^MemTotal:[[:space:]]*\([0-9]*\).*/\1/p' /proc/meminfo)
if [ -z "$MEM_TOTAL_KB" ]; then
    log_e "no meminfo"
    exit 1
fi

SIZE_MB=$(( MEM_TOTAL_KB * PCT / 100 / 1024 ))
[ "$SIZE_MB" -lt 16 ] && SIZE_MB=16

CUR_BYTES=$(cat /sys/block/zram0/disksize 2>/dev/null)
if [ -n "$CUR_BYTES" ] && [ $(( CUR_BYTES / 1024 / 1024 )) -eq "$SIZE_MB" ]; then
    log_i "size already ${SIZE_MB}M, nothing to do"
    exit 0
fi

log_i "resizing to ${SIZE_MB}M (was ${CUR_BYTES:-unknown} bytes)"

if ! swapoff /dev/block/zram0 2>>"$LOG"; then
    log_e "swapoff failed rc=$?"
fi
echo 1 > /sys/block/zram0/reset 2>>"$LOG" || log_e "reset failed"
echo lz4 > /sys/block/zram0/comp_algorithm 2>>"$LOG" || log_e "comp_algorithm failed"
if ! echo "${SIZE_MB}M" > /sys/block/zram0/disksize 2>>"$LOG"; then
    log_e "disksize failed"
    exit 1
fi
if ! mkswap /dev/block/zram0 2>>"$LOG"; then
    log_e "mkswap failed"
    exit 1
fi
if ! swapon /dev/block/zram0 2>>"$LOG"; then
    log_e "swapon failed"
    exit 1
fi
log_i "done, new disksize=$(cat /sys/block/zram0/disksize 2>/dev/null)"
