/**
 *  Is It Closed?
 *
 *  Copyright 2014 Greg Bronzert
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Easy Contact/Motion Sensor Alarms",
    namespace: "coolkev",
    author: "coolkev",
    description: "Get alerts when a door/window is left open when mode changes. And get alerts when a door/window is opened in specific modes",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-IsItSafe.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/App-IsItSafe@2x.png"
)


preferences {

    section("Get notifications when a door/window is left open when mode changes"){
    
		input "modeChangeSensors", "capability.contactSensor", title: "Open/close sensors", multiple: true, required: true
        input "modeChangeModes", "mode", title: "Only when mode changes to", multiple: true, required: false
    }
    
    section("Get notifications when a door/window is opened/closed"){
    
		input "alarmContactSensors", "capability.contactSensor", title: "Open/close sensors", multiple: true, required: true
        input "alarmContactModes", "mode", title: "Only when mode is", multiple: true, required: false
        
    }
    
    section("Get notifications when motion is detected"){
    
		input "alarmMotionSensors", "capability.motionSensor", title: "Motion sensors", multiple: true, required: true
        input "alarmMotionModes", "mode", title: "Only when mode is", multiple: true, required: false
    }
    
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
    unschedule()
	initialize()
}

def initialize() {
	
	if (modeChangeSensors != null) {
		subscribe(location, modeChangeHandler)
    }
    
    alarmContactSensors.each { subscribe(it, "contact", alarmContactSensorHandler) }
    
    alarmMotionSensors.each { subscribe(it, "motion.active", alarmMotionSensorHandler) }
        
}

def modeChangeHandler(evt) {
	log.debug "Mode change to: ${evt.value}"
    
    if (modeChangeModes.any{ it == evt.value } || modeChangeModes ==evt.value) {
    	log.debug "Checking ${modeChangeSensors.size()} contact sensors"
		
        def sensors = modeChangeSensors.size()>1 ? modeChangeSensors : [modeChangeSensors]
        sensors.each { 
        	log.debug "${it.displayName} is ${it.currentContact}"
            if (it.currentContact == "open")
            {
                def msg = "${it.displayName} was left open!"
                log.info msg
                //sendPush(msg)
            } else {
                log.debug "It wasn't open."
            }

    	}

    }
}


def alarmContactSensorHandler(evt) {
	log.debug "Contact Sensor Changed: ${evt.displayName} is ${evt.value}"
    
    if (alarmContactModes.any{ it == evt.value }) {

		def msg = "${evt.displayName} was "
        
        if (evt.value == "open")
        {
            msg += "opened"
            
        } else {
            msg += "closed"
        }

		log.info msg
        //sendPush(msg)


    }
}


def alarmMotionSensorHandler(evt) {
	log.debug "Motion Sensor Changed: ${evt.displayName} is ${evt.value}"
    
    if (alarmMotionModes.any{ it == evt.value }) {


        def msg = "${evt.displayName} was "
        log.info msg
        //sendPush(msg)
	

    }
}

