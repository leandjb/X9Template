
# Windows batch script to launch x9assist. 
# This batch script can be used as the basis to create user batch scripts. 

@echo off

# Change to the directory where this batch script was launched from. 
cd  "%~dp0"

# Launch x9assist. 
# javaw.exe is used so the application does not write to the console window.
# Start is used to further prevent the console window from being opened. 
# Maximum heap size (Xmx) is recommended as 7g for 64-bit and 1536m for 32-bit JVMs. 
 
start "" "C:\Program Files\Zulu\zulu-11\bin\javaw.exe" -Xmx7g -jar x9assist.jar

exit 
