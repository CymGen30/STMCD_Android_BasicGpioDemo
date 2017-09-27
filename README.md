# STMCD_Android_BasicGpioDemo
This is a basic demonstration of a USB CDC connection between an Android device and a Nucleo-F302R8 or Nucleo-F429ZI, allowing GPIO input and output management.

This demonstration integrates:
- a User Interface embedded in an Android application
- a basic communication protocol over USB CDC class
- a basic Cortex M4 GPIO management Firmware running on Nucleo-F302R8, communicating with User Interface through STLINK.
- a basic Cortex M4 GPIO management Firmware running on Nucleo-F429ZI, communicating with User Interface through built-in USB.

This demonstration provides source files of:
- Android application, 
- the Nucleo-F302R8 firmware,
- the Nucleo-F429ZI firmware.

# Nucleo-F302R8 firmware
The project has been setup thanks to CubeMX tool, available on www.st.com.

BasicGPIODemo.ioc is the CubeMX configuration file project.

Four IO ports are used:
- USART2 Rx (with interrupt) and Tx are used for communication with STLink at 115200/8/1.
- userButton is used as an input pin without interrupt
- LD2 is used for LED management

The project is compiled thanks to SW4STM32 tool, available on www.st.com. 

A mini-usb cable must be plugged on CN1 of NUCLEO-F302R8.

## Communication API
### Events
#### In case of Reset, STM32-Nucleo-F302R8 sends: 
"Reset: initialisation OK.\r\n"
#### In case of press on user button, STM32-Nucleo-F302R8 sends:
"Butt=0\r\n"
#### In case of release of user button, STM32-Nucleo-F302R8 sends:
"Butt=1\r\n"

### Commands
#### To set the Led ON, remote host has to send:
"SL=1\n"
#### To set the Led OFF, remote host has to send:
"SL=0\n"
#### both commands are acknoledged by STM32-Nucleo-F302R8 which sends:
"OK\r\n"
#### other commands are acknoledged by STM32-Nucleo-F302R8 which sends:
"ERROR\r\n"


# Nucleo-F429ZI firmware
The project has been setup thanks to CubeMX tool, available on www.st.com.

F429ZU-Usb-BasicGpioDemo.ioc is the CubeMX configuration file project.

Six IO ports are used:
- USB Vbus, Dm, and Dp are used for communication.
- userButton is used as an input pin without interrupt
- LD2 is used for LED management
- LD3 is used for LED management


The project is compiled thanks to SW4STM32 tool, available on www.st.com. 

A micro-usb cable must be plugged on CN3 of NUCLEO-F429ZI.

## Communication API
### Events
#### In case of press on user button, STM32-Nucleo-F302R8 sends:
"Butt=0\r\n"
#### In case of release of user button, STM32-Nucleo-F302R8 sends:
"Butt=1\r\n"

### Commands
#### To set the Led ON, remote host has to send:
"SL=1\n"
#### To set the Led OFF, remote host has to send:
"SL=0\n"
#### both commands are acknoledged by STM32-Nucleo-F302R8 which sends:
"OK\r\n"
#### other commands are acknoledged by STM32-Nucleo-F302R8 which sends:
"ERROR\r\n"


# Android application
The application is compiled thanks to AndroidStudio IDE tool:
https://developer.android.com/studio/index.html

The application uses usb-serial-for-android open source library, available in github:
https://github.com/mik3y/usb-serial-for-android

STM32 driver has been added to usb-serial-for-android library, in order to allow this demonstration.

The demonstration is fully embedded in MainActivity.java.

At start-up, the application displays the Device panel, and searches every 5 seconds an attached STM32 usb device.

When a STM32 usb device is found, it is now necessary to request permission to UsbManager.

As soon as permission is granted, connection is established, and Input and Output panels appear. 

The user can now press either Reset or User button on STM32-Nucleo-F302R8, either Android Orange LED button.

Enjoy.

