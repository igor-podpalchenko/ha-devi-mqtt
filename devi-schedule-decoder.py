def hex_to_time_range(hex_code):
    def decode_part(hex_part):
        if hex_part[0] in ['8', 'c']:
            minutes = "00" if hex_part[0] == '8' else "30"
            hours = int(hex_part[1], 16)
        elif hex_part[0] in ['9', 'd']:
            minutes = "00" if hex_part[0] == '9' else "30"
            hours = int(hex_part[1], 16) + 16
        else:
            raise ValueError("Invalid hex format")
        return f"{hours:02}:{minutes}"
    
    from_part = hex_code[:2]
    to_part = hex_code[2:]
    
    from_time = decode_part(from_part)
    to_time = decode_part(to_part)
    
    return f"{from_time} - {to_time}"

# Example usage
time_ranges = ["8081", "c0c1", "c1c2", "8184", "c3c4", "c5c6", "c7c8", "c9ca", "c8cb", "cecf", "d092", "90d2", "92d6", "9496", "d4d5", "96d7"]

for hex_code in time_ranges:
    print(f"{hex_code}: {hex_to_time_range(hex_code)}")

