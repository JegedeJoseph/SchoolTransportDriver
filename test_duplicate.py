import sqlite3
import os

source = sqlite3.connect(
    "file:school_transport_database?mode=ro",
    uri=True
)

test_file = "duplicate_test_copy.db"

for suffix in ["", "-wal", "-shm"]:
    path = test_file + suffix
    if os.path.exists(path):
        os.remove(path)

test = sqlite3.connect(test_file)
source.backup(test)
source.close()

row = test.execute("""
    SELECT studentId, studentName, eventType,
           latitude, longitude, gpsAccuracy,
           eventTimestamp, serviceDate, syncStatus
    FROM driver_events
    WHERE serviceDate = '2026-11-27'
      AND eventType = 'DROPOFF'
    LIMIT 1
""").fetchone()

if row is None:
    print("No current drop-off found. Test cannot proceed.")
else:
    print("Attempting to insert an existing drop-off again...")

    try:
        test.execute("""
            INSERT INTO driver_events (
                studentId, studentName, eventType,
                latitude, longitude, gpsAccuracy,
                eventTimestamp, serviceDate, syncStatus
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            row[0], row[1], row[2],
            row[3], row[4], row[5],
            row[6] + 1, row[7], row[8]
        ))

        test.commit()
        print("FAILED: Duplicate was accepted.")

    except sqlite3.IntegrityError as error:
        test.rollback()
        print("PASSED: Duplicate rejected by database.")
        print("SQLite response:", error)

    count = test.execute("""
        SELECT COUNT(*)
        FROM driver_events
        WHERE serviceDate = '2026-11-27'
          AND eventType = 'DROPOFF'
    """).fetchone()[0]

    print("Today's drop-off count after test:", count)

test.close()
