def time_range_to_hex(time_range):
    def encode_part(time):
        hours, minutes = map(int, time.split(":"))
        if hours < 16:
            if minutes == 0:
                hex_part = f"8{hours:X}"
            else:
                hex_part = f"c{hours:X}"
        else:
            hours -= 16
            if minutes == 0:
                hex_part = f"9{hours:X}"
            else:
                hex_part = f"d{hours:X}"
        return hex_part

    from_time, to_time = time_range.split(" - ")
    
    from_hex = encode_part(from_time)
    to_hex = encode_part(to_time)
    
    return from_hex + to_hex

# Example usage
time_ranges = ["00:00 - 01:00", "00:30 - 01:30", "01:30 - 02:30", "01:00 - 04:00", "03:30 - 04:30", 
               "05:30 - 06:30", "07:30 - 08:30", "09:30 - 10:30", "08:30 - 11:30", "14:30 - 15:30", 
               "16:30 - 18:00", "16:00 - 18:30", "18:00 - 22:30", "20:00 - 22:00", "20:30 - 21:30",
               "23:00 - 00:00", "22:00 - 23:30", "04:30 - 10:00", "19:00 - 24:00"]

for time_range in time_ranges:
    print(f"{time_range}: {time_range_to_hex(time_range)}")

