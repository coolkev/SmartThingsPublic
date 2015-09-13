/**
 *  Doorbell Notification
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
    name: "Doorbell Notification",
    namespace: "coolkev",
    author: "Kevin Lewis",
    description: "Get a notification when doorbell is pressed",
    category: "Convenience",
    iconUrl: "https://graph.api.smartthings.com/api/devices/icons/st.Home.home2-icn",
    iconX2Url: "https://graph.api.smartthings.com/api/devices/icons/st.Home.home2-icn?displaySize=2x",
    iconX3Url: "https://graph.api.smartthings.com/api/devices/icons/st.Home.home2-icn?displaySize=2x")


preferences {
	section("Which button is Doorbell?") {
            input "doorbell", title: "Button","device.button"
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
	// TODO: subscribe to attributes, devices, locations, etc.
    
    subscribe(doorbell, "button", doorbellPushed);
}

def doorbellPushed(evt) {

	log.debug "doorbellPushed"
    sendPush "Ding Dong - someone's at the door"
    
}

// TODO: implement event handlers