/**
 *  Smart Door/Motion Light
 *
 *  Copyright 2014 Kevin Lewis
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
    name: "Smart Garage Motion Light",
    namespace: "coolkev",
    author: "Kevin Lewis",
    description: "Turn your lights on when a open/close sensor opens and the space is dark.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/light_contact-outlet-luminance.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/light_contact-outlet-luminance@2x.png"
)

preferences {
	section("Sensors:") {
		input "contact1", "capability.contactSensor", title: "Inside Door sensor"
		input "contact2", "capability.contactSensor", title: "Garage Door sensor"
		input "motion1", "capability.motionSensor", title: "Motion sensor"
		input "luminance1", "capability.illuminanceMeasurement", title: "Illuminance Sensor"
        input "minLuminance", "number", title: "Minimum Illuminance Level", required: false, defaultValue: 100
        input "delaySeconds", "number", title: "Turn off light after X seconds", required: false, defaultValue: 10
	}
	section("Action") {
		input "switch1", "capability.switch", title: "Light switch"
	}
}

//normal sequence of events
// 1. Walk into garage: inside door opens, motion detected
// 2. Walk back into house: motion detected, inside door opens
// 3. Walk into garage, drive away: inside door opens, motion detected, garage door opens
// 4. Arrive home: garage door opens, motion detected, garage inside door opens then closes

// if there is no motion prior to door opening then must be entering garage from house
// if motion before door opening then must be entering the house from garage
// if no motion for X minutes after door closes, turn off the light

def installed()
{
	initialize()
}

def updated()
{
	unsubscribe()
	initialize()
}

def initialize()
{
	subscribe(contact1, "contact.open", contact1OpenHandler)
    subscribe(contact1, "contact.closed", contact1CloseHandler)
    subscribe(motion1, "motion", motionEvent)
   	subscribe(contact2, "contact.open", contact2OpenHandler)
   	subscribe(contact2, "contact.closed", contact2CloseHandler)
}

def contact1OpenHandler(evt) {
	
    log.debug "door opened"
    
    def currentMotion = motion1.currentState("motion")
    
    log.trace "currentMotion.value: $currentMotion.value"
    log.trace "currentMotion.date: $currentMotion.date"
    log.trace "currentMotion.date.time: $currentMotion.date.time"
    
    //def msElapsed = ((new Date()).time - currentMotion.date.time)
    //log.trace "msElapsed: $msElapsed"
    state.motionActiveWhenDoor1Opened = currentMotion.value=="active" || ((new Date()).time - currentMotion.date.time) < 5000
    log.trace "motionActiveWhenDoor1Opened=${state.motionActiveWhenDoor1Opened}"
    
	turnLightOnIfDark()
    
   // state.shouldturnLightOffIfNoMotion = false
    log.trace "unschedule turnLightOffIfNoMotion"
    unschedule( turnLightOffIfNoMotion )
}

def contact2OpenHandler(evt) {
	
    log.debug "garage door opened"
	
    def currentMotion = motion1.currentState("motion")
    
    log.trace "currentMotion.value: $currentMotion.value"
    log.trace "currentMotion.date: $currentMotion.date"
    log.trace "currentMotion.date.time: $currentMotion.date.time"
    
    //def msElapsed = ((new Date()).time - currentMotion.date.time)
    //log.trace "msElapsed: $msElapsed"
    state.motionActiveWhenDoor2Opened = currentMotion.value=="active" || ((new Date()).time - currentMotion.date.time) < 5000
    log.trace "motionActiveWhenDoor2Opened=${state.motionActiveWhenDoor2Opened}"
    
    
    turnLightOnIfDark()
    //state.shouldturnLightOffIfNoMotion = false
    log.trace "unschedule turnLightOffIfNoMotion"
    unschedule( turnLightOffIfNoMotion )
}


def contact1CloseHandler(evt) {

	log.debug "door closed [state.lightTurnedOn: ${state.lightTurnedOn}]"
	
	if (state.lightTurnedOn && switch1.currentValue("switch")=="on") {

        log.trace "delaying for $delaySeconds seconds"

		if (state.motionActiveWhenDoor1Opened) {
			//state.shouldturnLightOffIfNoMotion = true
			runIn(delaySeconds, turnLightOffIfNoMotion)
       	}
        
        else {
        	log.trace "not scheduling turnLightOffIfNoMotion because motionActiveWhenDoor1Opened is false"
        }
	}
}

def contact2CloseHandler(evt) {

	log.debug "garage door closed [state.lightTurnedOn: ${state.lightTurnedOn}]"
	
	if (state.lightTurnedOn && switch1.currentValue("switch")=="on") {

        state.shouldturnLightOffIfNoMotion = true

		if (state.motionActiveWhenDoor2Opened) {
			log.trace "delaying for $delaySeconds seconds"
		
        	//state.shouldturnLightOffIfNoMotion = true
			runIn(delaySeconds, turnLightOffIfNoMotion)
       	}
        else {
        	log.trace "not scheduling turnLightOffIfNoMotion because motionActiveWhenDoor2Opened is false"
        }
                
	}
}


def motionEvent(evt) {

	log.debug "motion event [value: ${evt.value}]"
	
	if (evt.value=="active") {
    	turnLightOnIfDark()
        
        //state.shouldturnLightOffIfNoMotion = false
        log.trace "unschedule turnLightOffIfNoMotion"
    
    	unschedule( turnLightOffIfNoMotion )        
     
    }
    
    
}

def turnLightOnIfDark() {

	def lightSensorState = luminance1.currentIlluminance
	log.debug "turnLightOnIfDark [luminance: $lightSensorState]"
	if (switch1.currentValue("switch")=="off" && lightSensorState != null && lightSensorState <= minLuminance) {
    
    	log.trace "switch.on() ... [luminance: ${lightSensorState}]"
        switch1.on()
		state.lightTurnedOn = true
        
	}
}

def turnLightOffIfNoMotion() {


    log.trace "turnLightOffIfNoMotion()"
    
    
    
    
    //if (state.shouldturnLightOffIfNoMotion) {
    	log.trace "turning switch off"
		switch1.off()
   		state.lightTurnedOn = false      
    //}
    //else {
    //	log.trace "not turning switch off because scheduled event should have been cancelled"        
    //}
   

}