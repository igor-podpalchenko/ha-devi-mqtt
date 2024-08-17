package io.homeassistant.binding.danfoss.internal.protocol;

public class Icon {

    public static class MsgClass {
        public static final int CLOUDIO_DEVICEGLOBAL = 0;
        public static final int TESTANDPRODUCTION = 1;
        public static final int WIFI = 2;
        public static final int MDG = 3;
        public static final int SOFTWAREUPDATE = 4;
        public static final int SYSTEM = 5;
        public static final int DUMMY_0_FIRST = 6;
        public static final int DUMMY_0_LAST = 49;
        public static final int DEVICEGLOBAL = 50;
        public static final int RAIL_FIRST = 51;
        public static final int RAIL_LAST = 53;
        public static final int OUTPUT_FIRST = 54;
        public static final int OUTPUT_LAST = 98;
        public static final int ROOM_FIRST = 99;
        public static final int ROOM_LAST = 143;
        public static final int REPEATER_FIRST = 144;
        public static final int REPEATER_LAST = 188;
        public static final int INTERNAL = 189;
        public static final int ALL_ROOMS = 190;
    }

// @formatter:off
    public static class MsgCode {                                                       /* Len Type      Meaning */
        // Class CLOUDIO_DEVICEGLOBAL
        public static final int GLOBAL_POWERCYCLECOUNTER                       = 2;
        public static final int GLOBAL_SOFTWAREBUILDREVISION                   = 8;
        public static final int GLOBAL_NUMBEROFENDPOINTS                       = 16;
        public static final int GLOBAL_DIVISIONID                              = 17;
        public static final int GLOBAL_BRANDID                                 = 18;    // 1   byte      Reads 0. Reserved ?
        public static final int GLOBAL_PRODUCTID                               = 19;    // 2   short     Reads 0x1001 on test device. The same as for DeviReg Smart.
        public static final int GLOBAL_COUNTRYISOCODE                          = 20;    // 4   String ?  Reads "00 " (with space) on device sold in Netherlands. Unused ?
        public static final int GLOBAL_REVISION                                = 21;
        public static final int GLOBAL_SERIALNUMBER                            = 22;
        public static final int GLOBAL_TEXTREVISION                            = 23;
        public static final int GLOBAL_AVAILABLEENDPOINTS                      = 24;
        public static final int GLOBAL_HARDWAREREVISION                        = 34;
        public static final int GLOBAL_SOFTWAREREVISION                        = 35;
        public static final int GLOBAL_PRODUCTIONDATE                          = 44;
        // Class TESTANDPRODUCTION
        public static final int TESTANDPRODUCTION_ERROR_CODE                   = 1008;
        public static final int TESTANDPRODUCTION_RESET_LEVEL                  = 4197;
        public static final int TESTANDPRODUCTION_DEVICE_DESCRIPTION           = 29696;
        public static final int TESTANDPRODUCTION_RESTARTSIMPLELINK            = 29699;
        public static final int TESTANDPRODUCTION_IS_RESTARTING_SIMPLELINK     = 29700;
        public static final int TESTANDPRODUCTION_TRANCEIVE                    = 29706;
        // Class WIFI
        public static final int WIFI_ERROR_CODE                                = 1009;
        public static final int WIFI_ROLE                                      = 29760;
        public static final int WIFI_RESET                                     = 29761;
        public static final int WIFI_OPERATIONAL_STATE                         = 29762;
        public static final int WIFI_CHANNEL                                   = 29763;
        public static final int WIFI_SSID_AP                                   = 29767;
        public static final int WIFI_CONNECTED_SSID                            = 29768;
        public static final int WIFI_CONNECT_SSID                              = 29769;
        public static final int WIFI_CONNECT_KEY                               = 29770;
        public static final int WIFI_CONNECT                                   = 29771;
        public static final int WIFI_NETWORK_PROCESSOR_POWER                   = 29773;
        public static final int WIFI_MAX_LONG_SLEEP                            = 29775;
        public static final int WIFI_TX_POWER                                  = 29776;
        public static final int WIFI_MDG_READY_FOR_RESTART                     = 29780;
        public static final int WIFI_NVM_READY_FOR_RESTART                     = 29781;
        public static final int WIFI_SCAN_SSID_0                               = 29782;
        public static final int WIFI_SCAN_SSID_1                               = 29783;
        public static final int WIFI_SCAN_SSID_2                               = 29784;
        public static final int WIFI_SCAN_SSID_3                               = 29785;
        public static final int WIFI_SCAN_SSID_4                               = 29786;
        public static final int WIFI_SCAN_SSID_5                               = 29787;
        public static final int WIFI_SCAN_SSID_6                               = 29788;
        public static final int WIFI_SCAN_SSID_7                               = 29789;
        public static final int WIFI_SCAN_SSID_8                               = 29790;
        public static final int WIFI_SCAN_SSID_9                               = 29791;
        public static final int WIFI_SCAN_STRENGTH_0                           = 29792;
        public static final int WIFI_SCAN_STRENGTH_1                           = 29793;
        public static final int WIFI_SCAN_STRENGTH_2                           = 29794;
        public static final int WIFI_SCAN_STRENGTH_3                           = 29795;
        public static final int WIFI_SCAN_STRENGTH_4                           = 29796;
        public static final int WIFI_SCAN_STRENGTH_5                           = 29797;
        public static final int WIFI_SCAN_STRENGTH_6                           = 29798;
        public static final int WIFI_SCAN_STRENGTH_7                           = 29799;
        public static final int WIFI_SCAN_STRENGTH_8                           = 29800;
        public static final int WIFI_SCAN_STRENGTH_9                           = 29801;
        public static final int WIFI_DISCONNECT_COUNT                          = 29802;
        public static final int WIFI_SKIP_AP_MODE                              = 29803;
        public static final int WIFI_CONNECTED_STRENGTH                        = 29804;
        // Class MDG
        public static final int MDG_ERROR_CODE                                 = 1010;
        public static final int MDG_CONNECTED_TO_SERVER                        = 29825;
        public static final int MDG_SHOULD_CONNECT                             = 29826;
        public static final int MDG_PAIRING_COUNT                              = 29828;
        public static final int MDG_PAIRING_0_ID                               = 29952;
        public static final int MDG_PAIRING_0_DESCRIPTION                      = 29953;
        public static final int MDG_PAIRING_0_PAIRING_TIME                     = 29954;
        public static final int MDG_PAIRING_0_PAIRING_TYPE                     = 29955;
        public static final int MDG_PAIRING_1_ID                               = 29957;
        public static final int MDG_PAIRING_1_DESCRIPTION                      = 29958;
        public static final int MDG_PAIRING_1_PAIRING_TIME                     = 29959;
        public static final int MDG_PAIRING_1_PAIRING_TYPE                     = 29960;
        public static final int MDG_PAIRING_2_ID                               = 29962;
        public static final int MDG_PAIRING_2_DESCRIPTION                      = 29963;
        public static final int MDG_PAIRING_2_PAIRING_TIME                     = 29964;
        public static final int MDG_PAIRING_2_PAIRING_TYPE                     = 29965;
        public static final int MDG_PAIRING_3_ID                               = 29967;
        public static final int MDG_PAIRING_3_DESCRIPTION                      = 29968;
        public static final int MDG_PAIRING_3_PAIRING_TIME                     = 29969;
        public static final int MDG_PAIRING_3_PAIRING_TYPE                     = 29970;
        public static final int MDG_PAIRING_4_ID                               = 29972;
        public static final int MDG_PAIRING_4_DESCRIPTION                      = 29973;
        public static final int MDG_PAIRING_4_PAIRING_TIME                     = 29974;
        public static final int MDG_PAIRING_4_PAIRING_TYPE                     = 29975;
        public static final int MDG_PAIRING_5_ID                               = 29977;
        public static final int MDG_PAIRING_5_DESCRIPTION                      = 29978;
        public static final int MDG_PAIRING_5_PAIRING_TIME                     = 29979;
        public static final int MDG_PAIRING_5_PAIRING_TYPE                     = 29980;
        public static final int MDG_PAIRING_6_ID                               = 29982;
        public static final int MDG_PAIRING_6_DESCRIPTION                      = 29983;
        public static final int MDG_PAIRING_6_PAIRING_TIME                     = 29984;
        public static final int MDG_PAIRING_6_PAIRING_TYPE                     = 29985;
        public static final int MDG_PAIRING_7_ID                               = 29987;
        public static final int MDG_PAIRING_7_DESCRIPTION                      = 29988;
        public static final int MDG_PAIRING_7_PAIRING_TIME                     = 29989;
        public static final int MDG_PAIRING_7_PAIRING_TYPE                     = 29990;
        public static final int MDG_PAIRING_8_ID                               = 29992;
        public static final int MDG_PAIRING_8_DESCRIPTION                      = 29993;
        public static final int MDG_PAIRING_8_PAIRING_TIME                     = 29994;
        public static final int MDG_PAIRING_8_PAIRING_TYPE                     = 29995;
        public static final int MDG_PAIRING_9_ID                               = 29997;
        public static final int MDG_PAIRING_9_DESCRIPTION                      = 29998;
        public static final int MDG_PAIRING_9_PAIRING_TIME                     = 29999;
        public static final int MDG_PAIRING_9_PAIRING_TYPE                     = 30000;
        public static final int MDG_PRIVATE_KEY                                = 30464;
        public static final int MDG_REVOKE_SPECIFIC_PAIRING                    = 30466;
        public static final int MDG_REVOKE_ALL_PAIRINGS                        = 30467;
        public static final int MDG_LICENCE_KEY                                = 30468;
        public static final int MDG_RANDOM_BYTES                               = 30469;
        public static final int MDG_CONNECTION_COUNT                           = 30470;
        public static final int MDG_PENDING_PAIRING                            = 30471;
        public static final int MDG_ADD_PAIRING                                = 30472;
        public static final int MDG_SERVER_DISCONNECT_COUNT                    = 30473;
        public static final int MDG_PAIRING_NOTIFICATION_TOKEN                 = 30474;
        public static final int MDG_PAIRING_NOTIFICATION_SUBSCRIPTIONS         = 30475;
        public static final int MDG_PAIRING_0_NOTIFICATION_TOKEN               = 30476;
        public static final int MDG_PAIRING_0_NOTIFICATION_SUBSCRIPTIONS       = 30477;
        public static final int MDG_PAIRING_2_NOTIFICATION_TOKEN               = 30478;
        public static final int MDG_PAIRING_2_NOTIFICATION_SUBSCRIPTIONS       = 30479;
        public static final int MDG_PAIRING_3_NOTIFICATION_TOKEN               = 30480;
        public static final int MDG_PAIRING_3_NOTIFICATION_SUBSCRIPTIONS       = 30481;
        public static final int MDG_PAIRING_4_NOTIFICATION_TOKEN               = 30482;
        public static final int MDG_PAIRING_4_NOTIFICATION_SUBSCRIPTIONS       = 30483;
        public static final int MDG_PAIRING_5_NOTIFICATION_TOKEN               = 30484;
        public static final int MDG_PAIRING_5_NOTIFICATION_SUBSCRIPTIONS       = 30485;
        public static final int MDG_PAIRING_6_NOTIFICATION_TOKEN               = 30486;
        public static final int MDG_PAIRING_6_NOTIFICATION_SUBSCRIPTIONS       = 30487;
        public static final int MDG_PAIRING_7_NOTIFICATION_TOKEN               = 30488;
        public static final int MDG_PAIRING_7_NOTIFICATION_SUBSCRIPTIONS       = 30489;
        public static final int MDG_PAIRING_8_NOTIFICATION_TOKEN               = 30490;
        public static final int MDG_PAIRING_8_NOTIFICATION_SUBSCRIPTIONS       = 30491;
        public static final int MDG_PAIRING_9_NOTIFICATION_TOKEN               = 30492;
        public static final int MDG_PAIRING_9_NOTIFICATION_SUBSCRIPTIONS       = 30493;
        public static final int MDG_PAIRING_1_NOTIFICATION_TOKEN               = 30494;
        public static final int MDG_PAIRING_1_NOTIFICATION_SUBSCRIPTIONS       = 30495;
        public static final int MDG_PAIRING_DESCRIPTION                        = 30496;
        public static final int MDG_PAIRING_TYPE                               = 30497;
        public static final int MDG_CONFIRM_SYSTEM_WIZARD_INFO                 = 30498;
        // Class SOFTWAREUPDATE
        public static final int SOFTWAREUPDATE_ERROR_CODE                      = 1011;
        public static final int SOFTWAREUPDATE_DOWNLOAD_PUSHED_UPDATE          = 30593;
        public static final int SOFTWAREUPDATE_CHECK_FOR_UPDATE                = 30594;
        public static final int SOFTWAREUPDATE_INSTALLATION_STATE              = 30595;
        public static final int SOFTWAREUPDATE_INSTALLATION_PROGRESS           = 30596;
        // Class SYSTEM
        public static final int SYSTEM_TIME_ISVALID                            = 29236;
        public static final int SYSTEM_TIME                                    = 29237;
        public static final int SYSTEM_TIME_OFFSET                             = 29238;
        public static final int SYSTEM_WINDOW_OPEN                             = 29242;
        public static final int SYSTEM_LOCAL_CONFIRM_REQUEST                   = 29249;
        public static final int SYSTEM_LOCAL_CONFIRM_RESPONSE                  = 29254;
        public static final int SYSTEM_TIME_OFFSET_TABLE                       = 29255;
        public static final int SYSTEM_MDG_CONNECT_PROGRESS                    = 29271;
        public static final int SYSTEM_MDG_CONNECT_PROGRESS_MAX                = 29272;
        public static final int SYSTEM_MDG_CONNECT_ERROR                       = 29273;
        public static final int SYSTEM_MDG_LOG_UNTIL                           = 29274;
        // Class DEVICEGLOBAL
        public static final int NUMBEROFENDPOINTS                              = 16;
        public static final int DIVISIONID                                     = 17;    //
        public static final int BRANDID                                        = 18;    // 1   byte      Reads 0. Reserved ?
        public static final int PRODUCTID                                      = 19;    // 2   short     Reads 0x4012
        public static final int COUNTRYISOCODE                                 = 20;    // 4   String ?  Reads "00 " (with space) on device sold in Netherlands. Unused ?
        public static final int REVISION                                       = 21;    // 4
        public static final int SERIALNUMBER                                   = 22;    // 4   int ?
        public static final int TEXTREVISION                                   = 23;
        public static final int AVAILABLE_ENDPOINTS                            = 24;
        public static final int DEVICEGLOBAL_RAILINDEX                         = 4097;  // 1   byte      ?
        public static final int DEVICEGLOBAL_RAILINDEXFORSYNCHRONIZATION       = 4098;  // ?   ?         We only suggest it to belong to this class. Never seen.
        // These are shared between classes RAIl, ROOM and REPEATER.
        // LASTCOMMUNICATIONTIME also seen under INTERNAL.
        public static final int LASTCOMMUNICATIONTIME                          = 4;     // 4   int ?     Units unknown.
        public static final int BINDINGPEER                                    = 128;   // 7   ?         Unknown
        // Class RAIL
        public static final int RAIL_POWER_CYCLE_COUNTER                       = 2;     // 1   byte ?
        public static final int RAIL_CURRENTTIME                               = 304;
        public static final int INTERNAL_FORWARDLINETEMPERATURE                = 776;
        public static final int INTERNAL_FORWARDLINETEMPERATURESETPOINT        = 785;
        public static final int INTERNAL_FORWARDLINETEMPERATURESETPOINTMINIMUM = 786;
        public static final int INTERNAL_FORWARDLINETEMPERATURESETPOINTMAXIMUM = 787;
        public static final int RAIL_CHECKININTERVALMIN                        = 800;
        public static final int RAIL_CHECKININTERVALMAX                        = 801;
        public static final int RAIL_CHECKINTEMPERATURECHANGE                  = 802;
        public static final int RAIL_ERRORCODE                                 = 1008;  // 2   short     Master controller error, see below
        public static final int RAIL_INPUTAWAY                                 = 4101;
        public static final int RAIL_INPUTHEATORCOOL                           = 4102;
        public static final int RAIL_INPUTDEWPOINT                             = 4103;
        public static final int RAIL_HEATINGCOOLINGCONFIGURATION               = 4213;
        public static final int RAIL_REFERENCEROOM                             = 4215;
        public static final int RAIL_REFERENCE_TEMPERATURE                     = 4216;
        public static final int RAIL_MODE                                      = 4352;
        public static final int RAIL_APP_NUMBER                                = 4353;
        public static final int RAIL_DISABLEDAYLIGHTSAVINGSTIME                = 5927;
        public static final int RAIL_DEGRADEDOUTPUT                            = 28691;
        public static final int RAIL_DEGRADEDTIME                              = 28695;
        public static final int RAIL_DEGRADEDFINALOUTPUT                       = 28696;
        public static final int RAIL_LOCALOUTPUTACTIVE                         = 28724;
        public static final int RAIL_PUMPMODE                                  = 28725;
        public static final int RAIL_PUMPSTARTDELAY                            = 28726;
        public static final int RAIL_PUMPSTOPDELAY                             = 28727;
        public static final int RAIL_GLOBALOUTPUTACTIVE                        = 28728;
        public static final int RAIL_BOILERMODE                                = 28730;
        public static final int RAIL_BOILERSTARTDELAY                          = 28731;
        public static final int RAIL_BOILERSTOPDELAY                           = 28732;
        public static final int RAIL_OUTPUTSALARMSTATUS                        = 28735;
        public static final int RAIL_OUTPUTSAVAILABLE                          = 28736;
        public static final int RAIL_OUTPUTSINUSE                              = 28737;
        public static final int RAIL_OUTPUTLEDINDICATION                       = 28739;
        public static final int RAIL_OUTPUTLEDINDICATIONTIME                   = 28740;
        public static final int RAIL_INPUTINUSE                                = 28741;
        // Class OUTPUT
        public static final int OUTPUT_REGULATIONFLOORDUTYCYCLE                = 780;
        public static final int OUTPUT_ERRORCODE                               = 1008;  // 2    short     Actuator error, see below
        public static final int OUTPUT_USEDBYROOM                              = 4104;
        public static final int OUTPUT_STATE_AUTO                              = 4608;
        public static final int OUTPUT_STATE_MANUAL                            = 4609;
        public static final int OUTPUT_PERIODTIME                              = 28692;
        public static final int OUTPUT_USERINTERRUPTION                        = 28705;
        public static final int OUTPUT_OUTPUTACTUATORTYPE                      = 28738;
        // Class ROOM
        public static final int ROOM_ROOMTEMPERATURE                           = 768;   // 2   decimal   Current room temperature
        public static final int ROOM_FLOORTEMPERATURE                          = 772;   // 2   decimal   Current floor temperature
        public static final int ROOM_OPERATIONMODE                             = 778;   // 1   byte      Operation mode. See RoomOperationMode values below.
        public static final int ROOM_BATTERYINDICATIONPERCENT                  = 783;   // 2   short     Battery charge level for wireless thermostat
        public static final int ROOM_CHECKININTERVAL                           = 800;   // 2   short
        public static final int ROOM_WAKEUPINTERVAL                            = 801;   // 2   short
        public static final int ROOM_CHECKINTEMPERATURECHANGE                  = 802;   // 2   short
        public static final int ROOM_DISPLAYEDTEMPERATURE                      = 809;   // 2   short
        public static final int ROOM_ERRORCODE                                 = 1008;  // 2   short     See below.
        public static final int ROOM_SETPOINTMINIMUM                           = 1287;
        public static final int ROOM_SETPOINTMAXIMUM                           = 1288;
        public static final int ROOM_SETPOINTATHOME                            = 1289;
        public static final int ROOM_SETPOINTAWAY                              = 1290;
        public static final int ROOM_SETPOINTASLEEP                            = 1291;
        public static final int ROOM_FLOORTEMPERATUREMINIMUM                   = 1292;
        public static final int ROOM_FLOORTEMPERATUREMAXIMUM                   = 1293;
        public static final int ROOM_ROOMMODE                                  = 4106;  // 1   byte      Current control state. See RoomMode values below
        public static final int ROOM_ROOMCONTROL                               = 4107;  // 1   byte      See RoomControl values below.
        public static final int ROOM_SCHEDULEMONDAY                            = 4108;  // 13
        public static final int ROOM_SCHEDULETUESDAY                           = 4109;
        public static final int ROOM_SCHEDULEWEDNESDAY                         = 4110;
        public static final int ROOM_SCHEDULETHURSDAY                          = 4111;
        public static final int ROOM_SCHEDULEFRIDAY                            = 4112;
        public static final int ROOM_SCHEDULESATURDAY                          = 4113;
        public static final int ROOM_SCHEDULESUNDAY                            = 4114;
        public static final int ROOM_HEATINGCOOLINGSTATE                       = 4115;  // 1   byte
        public static final int SETPOINTATHOMECOPY                             = 4117;  // 2
        public static final int SETPOINTAWAYCOPY                               = 4118;  // 2
        public static final int SETPOINTASLEEPCOPY                             = 4119;  // 2
        public static final int ROOM_OUTPUTGROUPSLOW                           = 4128;  // 2
        public static final int ROOM_OUTPUTGROUPMEDIUM                         = 4129;  // 2
        public static final int ROOM_OUTPUTGROUPFAST                           = 4130;  // 2
        public static final int ROOM_OUTPUTDUTYCYCLEINFOSLOW                   = 4144;  // 1
        public static final int ROOM_OUTPUTDUTYCYCLEINFOMEDIUM                 = 4145;  // 1
        public static final int ROOM_OUTPUTDUTYCYCLEINFOFAST                   = 4146;  // 1
        public static final int ROOM_CONTROLLERTYPE                            = 4210;  // 1   byte      Reads 0, perhaps reserved
        public static final int ROOM_COOLINGENABLED                            = 4212;  // 1   boolean ?
        public static final int ROOM_HEATINGCOOLINGCONFIGURATION               = 4213;  // 1
        public static final int ROOM_REFERENCEROOMENABLED                      = 4214;  // 1   boolean ?
        public static final int ROOM_ROOMSENSOROPERATIONOFF                    = 28675; // 1
        public static final int ROOM_ROOMSENSORTAMPERPROOF                     = 28928; // 1   boolean ?
        public static final int ROOMNAME                                       = 29250; // 33  String    Room name
        // Class REPEATER
        public static final int REPEATER_ERRORCODE                             = 1008;  // 2   short     See below
        // Class INTERNAL
        public static final int RAIL_INPUTPT1000                               = 4100;
        public static final int INTERNAL_PRODUCTCONFIGURATION                  = 4186;
        public static final int INTERNAL_TOUCH_STATE_AUTO                      = 4187;
        public static final int INTERNAL_TOUCH_STATE_MANUAL                    = 4188;
        public static final int INTERNAL_LED_STATE_AUTO                        = 4189;
        public static final int INTERNAL_LED_STATE_MANUAL                      = 4190;
        public static final int INTERNAL_BSP_TEST_CASE_SELECT                  = 4194;
        public static final int INTERNAL_BSP_TEST_CASE_STATUS                  = 4195;
        public static final int INTERNAL_BSP_TEST_CASE_CONTROL                 = 4196;
        public static final int INTERNAL_RESET_LEVEL                           = 4197;
        public static final int INTERNAL_WATCHDOG_RESET_COUNTER                = 4198;
        public static final int INTERNAL_SW_RESET_COUNTER                      = 4199;
        public static final int INTERNAL_ZWAVEMODULEDETECTED                   = 4217;
        public static final int INTERNAL_INPUT_1                               = 4354;
        public static final int INTERNAL_INPUT_2                               = 4355;
        public static final int INTERNAL_SENSOR_PT1000_RAW                     = 4356;
        public static final int INTERNAL_PWR1_AUTO                             = 4357;
        public static final int INTERNAL_PWR1_MANUAL                           = 4358;
        public static final int INTERNAL_RELAY_AUTO                            = 4359;
        public static final int INTERNAL_RELAY_MANUAL                          = 4360;
        public static final int INTERNAL_APP_TEST_ID                           = 4361;
        public static final int INTERNAL_APP_TEST_CONTROL                      = 4362;
        public static final int INTERNAL_APP_TEST_STATUS                       = 4363;
        public static final int INTERNAL_ALARM_NUMBER                          = 4364;
        public static final int INTERNAL_ALARM_TYPE                            = 4365;
        public static final int INTERNAL_ALARM_DATA                            = 4366;
        public static final int INTERNAL_MIXINGSHUNT_DUTY_AUTO                 = 4367;
        public static final int INTERNAL_MIXINGSHUNT_DUTY_MANUAL               = 4368;
        public static final int INTERNAL_MIXINGSHUNT_OPENING_AUTO              = 4369;
        public static final int INTERNAL_MIXINGSHUNT_OPENING_MANUAL            = 4370;
        public static final int INTERNAL_MIXINGSHUNTTWACURRENT                 = 4371;
        public static final int INTERNAL_MIXINGSHUNTTWARESISTORMINIMUM         = 4372;
        public static final int HECTARCH_SERVER_ADDRESS                        = 28743; // 1   byte      Reads 0. Strange.
        public static final int HECTARCH_SECURITY_KEY                          = 28744; // 17  byte[]    Well, some key, 16 bytes. What is hectarch ???
        public static final int HECTARCH_SERVER_ENDPOINT                       = 28745; // 1   byte      Reads 0.
        // Class ALL_ROOMS
        public static final int VACATION_SETPOINT                              = 2955;
        public static final int ROOMMODE                                       = 4106;
        public static final int SCHEDULEMONDAY                                 = 4108;
        public static final int SCHEDULETUESDAY                                = 4109;
        public static final int SCHEDULEWEDNESDAY                              = 4110;
        public static final int SCHEDULETHURSDAY                               = 4111;
        public static final int SCHEDULEFRIDAY                                 = 4112;
        public static final int SCHEDULESATURDAY                               = 4113;
        public static final int SCHEDULESUNDAY                                 = 4114;
        public static final int LIVINGZONE                                     = 4200;  // 7  byte[]    "Belongs to living zone" bit mask ???
        public static final int EXTENDEDATHOME                                 = 4201;  // 7  byte[]    "At home override" mode bit mask. One bit per room.
        public static final int HOUSE_NAME                                     = 29251; // 33 String    House name
        public static final int VACATION_PLAN                                  = 29252;
        public static final int PAUSE_SETPOINT                                 = 29253;
        public static final int PAUSE_PLAN                                     = 29256;
    }
// @formatter:on

    // ROOM_OPERATIONMODE values
    public static class RoomOperationMode {
        public static final byte COMFORT = 0;
        public static final byte FLOOR = 1;
        public static final byte DUAL = 2;
    }

    // ROOM_ROOMMODE values
    public static class RoomMode {
        public static final byte AtHome = 0;
        public static final byte Away = 1;
        public static final byte Asleep = 2;
        public static final byte Fatal = 3;
    }

    // ROOM_ROOMCONTROL values
    public static class RoomControl {
        public static final byte Manual = 0;
        public static final byte Auto = 1;
        public static final byte Schedule = 20;
    }

    // ROOM_ERRORCODE values
    public static class RoomError {
        public static final short OK = 0x0000; // Unconnected rooms also report this
        public static final short COMM_LOST = 0x0101;
    }

    // OUTPUT_ERRORCODE values
    public static class OutputError {
        public static final short OK = 0x0000;
        public static final short ACTUATOR_DEFECTIVE = 0x0101;
        public static final short ACTUATOR_DEFECTIVE2 = 0x0102;
    }

    // RAIL_ERRORCODE values
    public static class RailError {
        public static final short OK = 0x0000;
        public static final short RADIO_COMM_LOST = 0x0401;
        public static final short SLAVE_COMM_LOST = 0x1001;
        public static final short SLAVE_COMM_LOST2 = 0x1101;
        public static final short CM_COMM_LOST = 0x0801;
        public static final short EXPANSION_COMM_LOST = 0x0201;
    }

    // REPEATER_ERRORCODE values
    public static class RepeaterError {
        public static final short OK = 0x0000;
        public static final short COMM_LOST = 0x0101;
    }
}
