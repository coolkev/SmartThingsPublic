/**
 *  Arduino Alarm Controller
 *
 *  Copyright 2014 Kevin Lewis
 *
 * 	See arduino sketches here: https://github.com/coolkev/smartthings-alarm
 *	Need to manually add device types for "smartthings : Open/Closed Sensor" and "smartthings : Motion Detector" (use smartthings sample code)
 */
definition(
    name: "Arduino Alarm Controller",
    namespace: "coolkev",
    author: "Kevin Lewis",
    description: "Turn your hardwired alarm into smart sensors",
    category: "Safety & Security",
    iconUrl: "https://graph.api.smartthings.com/api/devices/icons/st.Home.home3-icn",
    iconX2Url: "https://graph.api.smartthings.com/api/devices/icons/st.Home.home3-icn?displaySize=2x",
    iconX3Url: "https://graph.api.smartthings.com/api/devices/icons/st.Home.home3-icn?displaySize=3x")

 
preferences {

	page(name: "deviceList")
    page(name: "deviceDetail")
    
    page(name: "preferencesPage")
    
    
}


def deviceList() {

    
    def zones = settings.findResults{it.key!="zoneCount" && it.key[0..3] == "zone" ? it.key.substring(4).toInteger() : null }.sort()
    
    def deviceCount = zones.size()
    log.debug "deviceCount=$deviceCount"
    
    dynamicPage(name: "deviceList", uninstall: true, install: true) {
        
        section("Which Arduino shield?") {
            input "arduino", title: "Shield","capability.polling"
        }    
        
        int maxZone = 0
        
        if (deviceCount>0) {
            section("Your virtual devices") {        
                for (int x=0;x<deviceCount;x++) {

					int zone = zones[x]
					
                    maxZone = zone
                    
                    def type = settings["typezone$zone"]==null ? "Open/Closed Sensor" : settings["typezone$zone"];

                    def description = type

                    def existingDevice = getChildDevice("zone$zone")

                    if (existingDevice) {

                        def currentState = getCurrentState(existingDevice, type)
                        description += " (currently $currentState)"

                    }

                    href(
                       name: "device$zone", 
                       title: settings["zone$zone"],
                       page: "deviceDetail", 
                       params: [
                           install: false,
                           zone: zone
                       ], 
                       description: description,
                       state: "complete"
                   )

                }
            }
        
        }
        
        section {        
                href(
                    name: "deviceNew", 
                    title: "Add new virtual device",
                    page: "deviceDetail", 
                    params: [
                        install: false,
                        zone: maxZone+1
                    ], 
                    description: "",
                    state: ""
                )
            }
            
            
        section {        
                href(
                    name: "preferences", 
                    title: "Preferences",
                    page: "preferencesPage",
                    description: "",
                    state: ""
                )
            }
    }
}

def deviceDetail(params) {
     /* 
     * firstPage included `params` in the href element that navigated to here. 
     * You must specify some variable name in the method declaration. (I used 'params' here, but it can be any variable name you want).
     * If you do not specify a variable name, there is no way to get the params that you specified in your `href` element. 
     */

    log.debug "params: ${params}"

	int zone = params.zone

	def title = settings["zone$zone"]==null ? "New Virtual Device" : settings["zone$zone"]
    
    dynamicPage(name: "deviceDetail", title: title, uninstall: false, install: false) {
        section {
                input "idzone$zone", title: "ID", "number", description:"Zone ID $zone", required: true
                
                input "zone$zone", title: "Name", "string", description:"Zone $zone", required: false
                input "typezone$zone", "enum", title: "Type", options:["Open/Closed Sensor","Motion Detector","Light Sensor","Temperature Sensor", "Button"], required: false
          
            }
        
    }
}


def preferencesPage() {


//log.debug "installModeConfigured=" + (settings["installModeConfigured"] ? "yes" : "no")
//log.debug "installModeUnconfigured=" + (settings["installModeUnconfigured"] ? "yes" : "no")

 dynamicPage(name: "preferencesPage", title: "Preferences", install:false, uninstall: false) {
    
    	section("Install Mode") {
        
        	paragraph "Send a push message with each device state change so you can configure your virtual devices"
            
        	 input(name: "installModeConfigured", type: "boolean", title: "Configured Devices")
             
             input(name: "installModeUnconfigured", type: "boolean", title: "Unconfigured Devices")
        
        }
    
    }
    


}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
    initialize()
}


def initialize() {

    
    // Listen to anything which happens on the device
    subscribe(arduino, "response", zonestatusChanged)
    
    
    def zones = settings.findResults{it.key!="zoneCount" && it.key[0..3] == "zone" ? it.key.substring(4).toInteger() : null }.sort()
    
    for (int i=0;i<zones.size();i++) {
    	
        int zone = zones[i]
        def name = "zone$zone"
		def value = settings[name]

        log.debug "checking device: ${name}, value: $value"

        def zoneType = settings["type" + name];

        if (zoneType == null || zoneType == "")
        {
            zoneType = "Open/Closed Sensor"
        }

        def existingDevice = getChildDevice(name)
        if(!existingDevice) {
            log.debug "creating device: ${name}"
            def childDevice = addChildDevice("smartthings", zoneType, name, null, [name: "Device.${name}", label: value, completedSetup: true])
        }
        else {
            //log.debug existingDevice.deviceType
            //existingDevice.type = zoneType
            existingDevice.label = value
            existingDevice.take()
            log.debug "device already exists: ${name}"

        }


    }
    
    
    def delete = getChildDevices().findAll { it.deviceNetworkId.startsWith("zone") && !settings[it.deviceNetworkId] }

    delete.each {
        log.debug "deleting child device: ${it.deviceNetworkId} ${it.name}"
        try {
       	 deleteChildDevice(it.deviceNetworkId)

		} catch (e) {
			log.error "Error deleting device: ${e}"
		}
    }
    

	runIn(300, "checkHeartbeat")
    
}

def uninstalled() {
	log.debug "uninstalled"
    //settings.clear()
    //removeChildDevices(getChildDevices())
}

private removeChildDevices(delete) {
    delete.each {
    	log.debug "deleting child device: ${it.deviceNetworkId}"
        deleteChildDevice(it.deviceNetworkId)
    }
}
def zonestatusChanged(evt)
{

 	log.debug "zonestatusChanged ${evt.value}"
   
   	def parts = evt.value.split(";");
    
    if (parts.size()==1) {
    	return
    }
    
    def states = parts*.toInteger()

	int eventId = states[0] as int
    
    def lastStates = state.lastStatus
    
    if (lastStates==null) {
    	lastStates = new int[states.size()]
    }
    else {
    	lastStates = lastStates*.toInteger()
	}
    
    int lastEventId = lastStates[0] as int

	if (lastEventId > eventId && (lastEventId < eventId+2)) {
    	log.debug "EventId out of order lastEventId: $lastEventId, eventId: $eventId"
   		state.lastStatus = states
	     return
    }
    
    state.lastStatus = states
	state.lastHeartbeat = now()
    
    for (int x=1;x<states.size();x++) {
    
    	int currentState = states[x]
        def lastState = lastStates[x]
        
        boolean isStateChange = currentState != lastState || lastState==null;
    	if (isStateChange) {
        
        	log.debug "zoneId $x state changed was: $lastState, now $currentState"        

            

    	}
        
        deviceStateChanged(x, currentState, isStateChange)
    }
        
}


def deviceStateChanged(int zoneId, int stateValue, boolean isStateChange) {


	//settings.each{ log.debug "key=${it.key}, value=${it.value}" }

    def zone = settings.find{it.key.size() > 6 && it.key[0..5] == "idzone" && it.value==zoneId }
    
    
    if (zone==null) {
    	
        log.debug "could not find device with zoneId=$zoneId"
        

        if (isStateChange && settings["installModeUnconfigured"]=="true") {
            sendPush("Unconfigured device state changed. zoneId: $zoneId, state: $stateValue")
        }

        
    	return
    }
    
	
    zone = zone.key.substring(6)
    
    log.debug "found device with zoneId=$zoneId zone=$zone"
    
	def deviceName = "zone$zone"
    def typeSettingName = "typezone$zone"

    
    def device = getChildDevice(deviceName)

    if (device)
    {

		def status;
        
        def zoneType = settings[typeSettingName];

        def eventName;

        if (zoneType == null || zoneType == "" || zoneType=="Open/Closed Sensor") {
            eventName = "contact"
            status = stateValue==1 ? "open" : "closed"
        }
        else if (zoneType=="Motion Detector")
        {
            eventName = "motion";
            status = (stateValue==1 || status=="active") ? "active" : "inactive"
        }   
        else if (zoneType=="Light Sensor")
        {
            eventName = "illuminance" 
            status = stateValue
        }   
        else if (zoneType=="Temperature Sensor") {
            eventName = "temperature"
            status = stateValue
        }

        else if (zoneType=="Button") {
        
        	if (stateValue==0) {
        		return
            }
            eventName = "button"
            status = "pushed"
        }

        if (isStateChange && settings["installModeConfigured"]=="true") {
        	sendPush("Configured device state changed. device: ${device.label}, zoneId: $zoneId, rawstate: $stateValue, status: $status")
        }

        log.debug "$device statusChanged $status"

        device.sendEvent(name: eventName, value: status)
    }
    else {

        log.debug "couldn't find device for zone ${zone}"

    }

}


def checkHeartbeat() {


	def elapsed = now() - state.lastHeartbeat;
    log.debug "checkHeartbeat elapsed: $elapsed"
    
	if (elapsed > 30000) {
    
    	log.debug "Haven't received heartbeat in a while - alarm is offline"
        sendPush("Arduino Alarm appears to be offline - haven't received a heartbeat in over 5 minutes");
    }

	
}



def getCurrentState(device, type) {


switch (type) {

	case "Open/Closed Sensor":
    
    	return device.currentContact
    
    case "Motion Detector":
    
    	return device.currentMotion
        
    case "Light Sensor":
    
    	return device.currentIlluminance
        
    case "Temperature Sensor":
    
    	return device.currentTemperature
        
    case "Button":

		return device.currentButton
        
}

}