@ECHO OFF
:: Compare x9 files using Meld, WinMerge, or UltraCompare 
:: X9Ware LLC 
:: Modification date 12.22.2017 

:: Check for WinMerge 

SET "commandExe=c:/Program Files/WinMerge/WinMergeU.exe"
IF EXIST "%commandExe%" goto :_WinMerge

SET "commandExe=c:/Program Files (x86)/WinMerge/WinMergeU.exe"
IF EXIST "%commandExe%" goto :_WinMerge

:: Check for Meld 

SET "commandExe=c:/Program Files/Meld/Meld.exe"
IF EXIST "%commandExe%" goto :_Meld

SET "commandExe=c:/Program Files (x86)/Meld/Meld.exe"
IF EXIST "%commandExe%" goto :_Meld

:: Check for UltraCompare 

SET "commandExe=c:/Program Files/IDM Computer Solutions/UltraCompare/uc.exe"
IF EXIST "%commandExe%" goto :_UltraCompare

SET "commandExe=c:/Program Files (x86)/IDM Computer Solutions/UltraCompare/uc.exe"
IF EXIST "%commandExe%" goto :_UltraCompare 

ECHO compare tool not found
EXIT /B 11

:_WinMerge

:: ECHO file1 %1%
:: ECHO file2 %2%

IF /I %1 == "-isDefined" EXIT /B 10
IF /I "%-1" == "-isDefined" EXIT /B 10  

ECHO WinMerge executing %commandExe%
"%commandExe%"  /e /x /wl /wr  "%~f1"  "%~f2" 
EXIT /B 0

:_Meld

:: ECHO file1 %1%
:: ECHO file2 %2%

IF /I %1 == "-isDefined" EXIT /B 10
IF /I "%-1" == "-isDefined" EXIT /B 10  

ECHO Meld executing %commandExe%
"%commandExe%"  "%~f1"  "%~f2" 
EXIT /B 0

:_UltraCompare

:: ECHO file1 %1%
:: ECHO file2 %2%

IF /I %1 == "-isDefined" EXIT /B 10
IF /I "%-1" == "-isDefined" EXIT /B 10  

ECHO UltraCompare executing %commandExe%
"%commandExe%"  /t /a  "%~f1"  "%~f2" 
EXIT /B 0
