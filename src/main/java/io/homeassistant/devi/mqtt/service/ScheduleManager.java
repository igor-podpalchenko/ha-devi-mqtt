package io.homeassistant.devi.mqtt.service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ScheduleManager {
    private static final int DAYS_IN_WEEK = 7;
    private static final int FIRST_PART_LENGTH = 4; // First part contains 4 bytes for each day
    private static final int SECOND_PART_LENGTH = 6; // Second part contains 6 bytes for each day
    private static final int DAY_BYTE_LENGTH = FIRST_PART_LENGTH + SECOND_PART_LENGTH; // Total bytes per day

    private byte[] weeklySchedule = new byte[DAYS_IN_WEEK * DAY_BYTE_LENGTH]; // 7 days, 10 bytes per day
    private int partsReceivedMask = 0; // Bitmask to track received parts: 1 for first part, 2 for second part

    private static final String[] DAY_NAMES = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};

    public boolean putScheduleBytes(byte[] array) {
        if (array.length != FIRST_PART_LENGTH * DAYS_IN_WEEK && array.length != SECOND_PART_LENGTH * DAYS_IN_WEEK) {
            throw new IllegalArgumentException("Invalid array length");
        }

        if (array.length == FIRST_PART_LENGTH * DAYS_IN_WEEK) {
            for (int day = 0; day < DAYS_IN_WEEK; day++) {
                // Copy first part
                System.arraycopy(array, day * FIRST_PART_LENGTH, weeklySchedule, day * DAY_BYTE_LENGTH, FIRST_PART_LENGTH);
            }
            // Set bit in mask to indicate first part received
            partsReceivedMask |= 1;
        } else if (array.length == SECOND_PART_LENGTH * DAYS_IN_WEEK) {
            for (int day = 0; day < DAYS_IN_WEEK; day++) {
                // Copy second part
                System.arraycopy(array, day * SECOND_PART_LENGTH, weeklySchedule, day * DAY_BYTE_LENGTH + FIRST_PART_LENGTH, SECOND_PART_LENGTH);
            }
            // Set bit in mask to indicate second part received
            partsReceivedMask |= 2;
        }

        // Check if both parts have been received
        return partsReceivedMask == 3;
    }

    public byte[] getWeeklySchedule() {
        return weeklySchedule;
    }

    public String toJson() {
        Map<String, String[]> scheduleMap = new HashMap<>();

        for (int day = 0; day < DAYS_IN_WEEK; day++) {
            String[] timePeriods = new String[5];
            int count = 0;
            for (int period = 0; period < 5; period++) {
                int startIdx = day * DAY_BYTE_LENGTH + period * 2;
                int endIdx = startIdx + 2;
                String hex = bytesToHex(weeklySchedule, startIdx, endIdx).toLowerCase();
                if (!hex.equals("0000")) {
                    timePeriods[count++] = hex;
                }
            }
            // Only include non-null periods
            String[] filteredPeriods = new String[count];
            System.arraycopy(timePeriods, 0, filteredPeriods, 0, count);
            scheduleMap.put(DAY_NAMES[day], filteredPeriods);
        }

        Gson gson = new Gson();
        return gson.toJson(scheduleMap);
    }

    public boolean fromJson(String jsonString) {
        try {
            Gson gson = new Gson();
            Map<String, ArrayList<String>> scheduleMap = gson.fromJson(jsonString, Map.class);

            for (int day = 0; day < DAYS_IN_WEEK; day++) {
                ArrayList<String> timePeriods = scheduleMap.get(DAY_NAMES[day]);

                // Validations start

                // - ranges null array
                // - more than 5 ranges in array
                // - single range not equal 4 symbols
                // - range overlaps
                // - range duplicates

                if( timePeriods == null ) {
                    throw new IllegalArgumentException("Array of time periods is missing for day " + DAY_NAMES[day]);
                }

                if(timePeriods.size() > 5) {
                    throw new IllegalArgumentException("Invalid number of time periods");
                }

                List<String> timePeriodsHumanReadable = timePeriods.stream().map(
                        ScheduleManager::decodeTimeRange).collect(Collectors.toList()
                );

                if(!isValidSchedule(timePeriods)) {
                    throw new IllegalArgumentException("Invalid schedule, overlaps or duplicates detected");
                }

                // Validations end

                for (int period = 0; period < timePeriods.size(); period++) {
                    int startIdx = day * DAY_BYTE_LENGTH + period * 2;
                    int endIdx = startIdx + 2;
                    hexToBytes(timePeriods.get(period), weeklySchedule, startIdx, endIdx);
                }
            }



            return true;
        } catch (JsonSyntaxException | NullPointerException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void printSchedule() {

        System.out.print("Raw schedule: ");
        for (int i = 0; i < weeklySchedule.length; i++) {
            System.out.printf("%02x", weeklySchedule[i]);
        }
        System.out.println();
    }

    public void printScheduleHumanReadable() {

        for (int i = 0; i < weeklySchedule.length; i++) {

            int dayOffWeek = i % 10;
            if(dayOffWeek == 0) {
                System.out.print(DAY_NAMES[i/10] + ": ");
            }

            System.out.printf("%02x", weeklySchedule[i]);
            if ((i + 1) % 2 == 0) {
                System.out.print(" ");
            }
            if ((i + 1) % 10 == 0) {
                System.out.println();
            }
        }

    }

    public Map<String, byte[]> splitScheduleToParts() {
        byte[] firstPart = new byte[FIRST_PART_LENGTH * DAYS_IN_WEEK];
        byte[] secondPart = new byte[SECOND_PART_LENGTH * DAYS_IN_WEEK];

        for (int day = 0; day < DAYS_IN_WEEK; day++) {
            System.arraycopy(weeklySchedule, day * DAY_BYTE_LENGTH, firstPart, day * FIRST_PART_LENGTH, FIRST_PART_LENGTH);
            System.arraycopy(weeklySchedule, day * DAY_BYTE_LENGTH + FIRST_PART_LENGTH, secondPart, day * SECOND_PART_LENGTH, SECOND_PART_LENGTH);
        }

        Map<String, byte[]> parts = new HashMap<>();
        parts.put("firstPart", firstPart);
        parts.put("secondPart", secondPart);
        return parts;
    }

    private static String bytesToHex(byte[] bytes, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    private static void hexToBytes(String hex, byte[] bytes, int start, int end) {
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[start + i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
    }

    private static String decodeTimeRange(String hexCode) {

        if (hexCode.length() != 4) {
            throw new IllegalArgumentException("Hex part must be exactly 4 characters long.");
        }

        if(hexCode.equals("0000")) {
            return "N/A - N/A";
        }

        String fromPart = hexCode.substring(0, 2);
        String toPart = hexCode.substring(2, 4);

        String fromTime = decodePart(fromPart);
        String toTime = decodePart(toPart);

        return fromTime + " - " + toTime;
    }

    private static String decodePart(String hexPart) {
        int hours;
        String minutes;

        switch (hexPart.charAt(0)) {
            case '8':
            case '9':
                minutes = "00";
                hours = Integer.parseInt(String.valueOf(hexPart.charAt(1)), 16);
                if (hexPart.charAt(0) == '9') {
                    hours += 16;
                }
                break;
            case 'c':
            case 'd':
                minutes = "30";
                hours = Integer.parseInt(String.valueOf(hexPart.charAt(1)), 16);
                if (hexPart.charAt(0) == 'd') {
                    hours += 16;
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid hex format: " + hexPart);
        }

        return String.format("%02d:%s", hours, minutes);
    }


    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static boolean isValidSchedule(List<String> timeRanges) {

            long[] bitMasks = new long[timeRanges.size()];

            // Convert each time range to a 48-bit bitmask
            for (int i = 0; i < timeRanges.size(); i++) {
                bitMasks[i] = timeRangeToBitMask(timeRanges.get(i));
            }

            // Check for overlaps and duplicates using bitwise operations
            for (int i = 0; i < bitMasks.length; i++) {
                for (int j = i + 1; j < bitMasks.length; j++) {
                    // Check for overlaps
                    if ((bitMasks[i] & bitMasks[j]) != 0) {
                        return false;
                    }
                }
            }

        return true;
    }

    private static long timeRangeToBitMask(String hexRange) {
        String decodedRange = decodeTimeRange(hexRange);
        String[] splitRange = decodedRange.split(" - ");

        int startBit = timeToBitIndex(splitRange[0]);
        int endBit = timeToBitIndex(splitRange[1]);

        // Create a bitmask with bits set from startBit to endBit - 1
        long bitmask = 0;
        for (int i = startBit; i < endBit; i++) {
            bitmask |= (1L << i);
        }
        return bitmask;
    }

    private static int timeToBitIndex(String time) {
        String[] parts = time.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);

        // Each hour is represented by 2 bits (30-minute intervals)
        return hours * 2 + (minutes == 30 ? 1 : 0);
    }


    private static final byte[] zeroScheduleWeek1 = new byte[28];
    private static final byte[] zeroScheduleWeek2 = new byte[42];

    public static byte[] getZeroScheduleWeek1() {
        return  zeroScheduleWeek1;
    }

    public static byte[] getZeroScheduleWeek2() {
        return  zeroScheduleWeek2;
    }

    public static void main(String[] args) {
        ScheduleManager scheduleManager = new ScheduleManager();

        String firstPartHex = "868890d6868890d6868890d6868890d6868890d687d6000087d60000";
        String secondPartHex = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000";

        // Loop through the string and extract 4-symbol words
        for (int i = 0; i < firstPartHex.length(); i += 4) {
            String word = firstPartHex.substring(i, i + 4);
            System.out.println(decodeTimeRange(word));
        }

        byte[] firstPart = hexStringToByteArray(firstPartHex);
        byte[] secondPart = hexStringToByteArray(secondPartHex);

        boolean firstPartLoaded = scheduleManager.putScheduleBytes(firstPart);
        boolean secondPartLoaded = scheduleManager.putScheduleBytes(secondPart);

        System.out.println(String.format("First part: %s", firstPartHex));
        System.out.println(String.format("Second part: %s", secondPartHex));

        System.out.println(String.format("Combined: %s", bytesToHex(
                scheduleManager.getWeeklySchedule(), 0, scheduleManager.getWeeklySchedule().length))
        );

        String json = scheduleManager.toJson();
        System.out.println("Schedule in JSON format: " + json);

        boolean fromJsonSuccess = scheduleManager.fromJson(json);
        System.out.println("Loaded from JSON: " + fromJsonSuccess);

        scheduleManager.printScheduleHumanReadable();

        Map<String, byte[]> parts = scheduleManager.splitScheduleToParts();
        System.out.println("First Part Hex: " + bytesToHex(parts.get("firstPart"), 0, parts.get("firstPart").length));
        System.out.println("Second Part Hex: " + bytesToHex(parts.get("secondPart"), 0, parts.get("secondPart").length));
    }

}
