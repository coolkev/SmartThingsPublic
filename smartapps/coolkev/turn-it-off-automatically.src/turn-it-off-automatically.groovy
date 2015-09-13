/**
 *  Copyright 2015 SmartThings
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
 *  Turn It On For 5 Minutes
 *  Turn on a switch when a contact sensor opens and then turn it back off 5 minutes later.
 *
 *  Author: SmartThings
 */
definition(
    name: "Turn It Off Automatically",
    namespace: "coolkev",
    author: "coolkev",
    description: "Turn off a light automatically after a period of time",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/light_contact-outlet.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_contact-outlet@2x.png"
)

preferences {
	section("Settings"){
		input "switch1", "capability.switch", title: "When this switch is turned on", required: true
        input "delayMinutes", "number", title: "Turn it off after X minutes", required: true, defaultValue: 10
        
	}
    section("Only if no motion detected (optional)") {
    	input "motion1", "capability.motionSensor", title: "Motion Sensor", required: false
    }
    section("Only between these times (optional)") {
    	input "startTime", "time", title: "Start Time", required: false
        input "stopTime", "time", title: "Stop Time", required: false
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	subscribe(switch1, "switch", switchHandler)
    if (motion1!=null) {
    	subscribe(motion1, "motion.active", motionHandler)
    }
}

def updated(settings) {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	subscribe(switch1, "switch", switchHandler)
    if (motion1!=null) {
    	subscribe(motion1, "motion.active", motionHandler)
    }
}

def switchHandler(evt) {
	log.debug "switchHandler: ${evt.value}"
    
    if (evt.value=="on") {

        if (startTime!=null && stopTime!=null) {
            def start = timeToday(startTime, location?.timeZone)
            def stop = timeToday(stopTime, location?.timeZone)
            def now = new Date()

            if (start.before(now) && stop.after(now)) {
                log.debug "Within start/stop times, will schedule turn off..."            

            }
            else {
                log.debug "Outside start/stop times, will not schedule turn off."
                return
            }
        }

        log.debug "Switch turned on... will automatically turn off in $delayMinutes minutes"
        def delaySeconds = delayMinutes * 60
        runIn(delaySeconds, turnOffSwitch)

    }
    else {
    	log.debug "Switch turned off "
		unschedule("turnOffSwitch")
    }
}


def motionHandler(evt) {

	log.debug "motionHandler: ${evt.value}"
    def switchVal = switch1.currentValue("switch")
	log.debug "switchVal: ${switchVal}"

	if (switch1.currentValue("switch")=="on") {

        if (startTime!=null && stopTime!=null) {
            def start = timeToday(startTime, location?.timeZone)
            def stop = timeToday(stopTime, location?.timeZone)
            def now = new Date()

            if (start.before(now) && stop.after(now)) {
                log.debug "Within start/stop times, will schedule turn off..."            

            }
            else {
                log.debug "Outside start/stop times, will not schedule turn off."
                return
            }
        }

        unschedule("turnOffSwitch")

        log.debug "Motion active, will automatically turn off in $delayMinutes minutes"
        def delaySeconds = delayMinutes * 60
        runIn(delaySeconds, turnOffSwitch)
	}

}

def turnOffSwitch() {
	log.debug "turnOffSwitch()"            
	switch1.off()
}