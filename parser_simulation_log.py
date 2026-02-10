import csv

in_path = "simulation.log"
out_path = "requests.csv"

with open(in_path, "r", encoding="utf-8", errors="replace") as fin, \
     open(out_path, "w", newline="", encoding="utf-8") as fout:
    w = csv.writer(fout)
    w.writerow([
        "scenario", "userId", "groupHierarchy", "requestName",
        "startTs", "endTs", "status", "extraInfo"
    ])

    for line in fin:
        parts = line.rstrip("\n").split("\t")
        # Gatling usa registros tab-separated. El índice exacto puede variar por versión.
        if len(parts) < 9:
            continue
        scenario, user_id, record_type = parts[0], parts[1], parts[2]
        if record_type != "REQUEST":
            continue
        group_hierarchy = parts[3]
        request_name = parts[4]
        start_ts = parts[5]
        end_ts = parts[6]
        status = parts[7]
        extra = parts[8] if len(parts) > 8 else ""
        w.writerow([scenario, user_id, group_hierarchy, request_name, start_ts, end_ts, status, extra])

print(f"Wrote {out_path}")

