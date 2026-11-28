import sqlite3

database = "file:school_transport_database?mode=ro"
connection = sqlite3.connect(database, uri=True)

print("DATABASE VERSION:")
print(connection.execute("PRAGMA user_version;").fetchone()[0])

print("\nINDEXES:")
indexes = connection.execute(
    "PRAGMA index_list('driver_events');"
).fetchall()

for index in indexes:
    print(index)

print("\nINDEX DEFINITIONS:")
definitions = connection.execute("""
    SELECT name, sql
    FROM sqlite_master
    WHERE type = 'index'
      AND tbl_name = 'driver_events';
""").fetchall()

for definition in definitions:
    print(definition)

print("\nEVENT COUNTS:")
print(connection.execute("""
    SELECT serviceDate, eventType, COUNT(*)
    FROM driver_events
    GROUP BY serviceDate, eventType
    ORDER BY serviceDate, eventType;
""").fetchall())

connection.close()
