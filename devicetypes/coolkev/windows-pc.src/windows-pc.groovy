/**
 *  Windows PC
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
metadata {
	definition (name: "Windows PC", namespace: "coolkev", author: "Kevin Lewis") {
		capability "Polling"
        capability "Refresh"
		capability "Image Capture"
		
        capability "switch"
        attribute "level", "number"
        
        command "getVolume"
        command "setLevel", ["number"]
        
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles {

		carouselTile("cameraDetails", "device.image", width: 3, height: 2) { }

		standardTile("take", "device.image", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
			state "take", label: "Take", action: "Image Capture.take", icon: "st.camera.camera", backgroundColor: "#FFFFFF", nextState:"taking"
			state "taking", label:'Taking', action: "", icon: "st.camera.take-photo", backgroundColor: "#53a7c0"
			state "image", label: "Take", action: "Image Capture.take", icon: "st.camera.camera", backgroundColor: "#FFFFFF", nextState:"taking"
		}

		standardTile("switch", "device.switch", width: 1, height: 1, canChangeIcon: true) {
			state "on", label: '${name}', action: "switch.off", icon: "st.Electronics.electronics18", backgroundColor: "#79b821", nextState:"turningOff"
			state "off", label: '${name}', icon: "st.Electronics.electronics18", backgroundColor: "#ffffff"
            state "turningOff", label:'${name}', icon:"st.Electronics.electronics18", backgroundColor:"#ffffff"
		}
        
        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
          state "refresh", action:"polling.poll", icon:"st.secondary.refresh"
        }
        
        controlTile("levelSliderControl", "device.level", "slider", height: 1, width: 3, inactiveLabel: false) {
            state "level", action:"setLevel", backgroundColor:"#ffffff"
        }
        
        main "switch"
        details(["cameraDetails", "take","switch","refresh","levelSliderControl"])
    }
    
   
}

def installed() {

	updated()
}

def updated() {

	log.debug "updated"

    if (state.laststatechange==null) {
        state.laststatechange = 0
    }
    
    
    if (state.lastpolltime==null) {
        state.lastpolltime = 0
    }

	//subscribeAction("/api/power/setSubscribe")
 	//subscribe(location, null, lanResponseHandler, [filterEvents:false])
 
	//poll()
}


private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

private subscribeAction(path, callbackPath="") {
    log.trace "subscribe($path, $callbackPath)"
    def address = getCallBackAddress()
    def ip = getHostAddress()

    def result = new physicalgraph.device.HubAction(
        method: "SUBSCRIBE",
        path: path,
        headers: [
            HOST: ip,
            CALLBACK: "<http://${address}/notify$callbackPath>",
            NT: "rest:event",
            TIMEOUT: "Second-28800"
        ]
    )

    log.trace "SUBSCRIBE $path"

    return result
}

//def lanResponseHandler(evt) {
//	log.debug "In response handler"
//	log.debug "I got back ${evt.description}"
//}

def take() {
	log.debug "Executing 'take'"
	
    
    def hubAction = deviceAction("/api/power/screenshot")
    hubAction.options = [outputMsgToS3:true]
    hubAction
    
}

def off() {
    log.debug "Executing 'off'"

    deviceAction("/api/power/sleep")
}

def refresh() {

	log.debug "Executing 'refresh'"

	poll()
    
    //subscribeAction("/api/power/setSubscribe")


}
def poll() {
    log.debug "Executing 'poll'"

	log.debug "state.lastpolltime= ${state.lastpolltime}"

	log.debug device.currentValue("switch")
    
	//if haven't received response from poll in > 1 min then assume its offline
	if (device.currentValue("switch")=="on" && state.lastpolltime > state.laststatechange + 60000)
    {
    	log.debug "haven't received poll response in > 1 min - setting state to off"
		sendEvent (name: "switch", value: "off")
    }
    else {
    
    	sendEvent (name: "switch", value: device.currentValue("switch"))
    
    }
	state.lastpolltime = now()
    
    
    [deviceAction("/api/power/status"), getVolume() ]   
    
    
}

private deviceAction(path) {

    def host = getHostAddress()


	log.debug "deviceAction $host $path"
 	def hubAction = new physicalgraph.device.HubAction(
		method: "GET",
		path: path,
		headers: [HOST:host]
	)
    
    hubAction
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress() {
	def parts = device.deviceNetworkId.split(":")
	def ip = convertHexToIP(parts[0])
	def port = convertHexToInt(parts[1])
	return ip + ":" + port
}


private def parseEventMessage(String description) {
    def event = [:]
    def parts = description.split(',')
    parts.each { part ->
        part = part.trim()

        if (part.startsWith('headers')) {
            part -= "headers:"
            def valueString = part.trim()
            if (valueString) {
                event.headers = valueString
            }
        }
        else if (part.startsWith('body')) {
            part -= "body:"
            def valueString = part.trim()
            if (valueString) {
                event.body = valueString
            }
        }
    }

    event
}

def parse(String description) {
    log.debug "Parsing '${description}'"

	def descMap = parseDescriptionAsMap(description)

    //Image
	if (descMap["bucket"] && descMap["key"]) {
    	log.debug "putImageInS3"
		putImageInS3(descMap)
	}
	else {

        def parsedEvent= parseEventMessage( description)

        def headerString = new String(parsedEvent.headers.decodeBase64())
        def bodyString = new String(parsedEvent.body.decodeBase64())

        def json = new groovy.json.JsonSlurper().parseText( bodyString)

        log.trace json

        if( json.msg)
        {
            if( json.msg.startsWith("state"))
            {
                state.laststatechange = now()

                log.trace "Setting state"

                sendEvent (name: json.name, value: json.state, isStateChange: true)
            }
        }
        
    }
}



def parseDescriptionAsMap(description) {
	description.split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
}

def putImageInS3(map) {

	def s3ObjectContent

	try {
		def imageBytes = getS3Object(map.bucket, map.key + ".jpg")

		if(imageBytes)
		{
			s3ObjectContent = imageBytes.getObjectContent()
			def bytes = new ByteArrayInputStream(s3ObjectContent.bytes)
			storeImage(getPictureName(), bytes)
		}
	}
	catch(Exception e) {
		log.error e
	}
	finally {
		//Explicitly close the stream
		if (s3ObjectContent) { s3ObjectContent.close() }
	}
}

private getPictureName() {
  def pictureUuid = java.util.UUID.randomUUID().toString().replaceAll('-', '')
  "image" + "_$pictureUuid" + ".jpg"
}


def getVolume() {

	deviceAction("/api/volume")

}

def setLevel(val) {

	log.trace "setLevel($val)"
	def v = Math.max(Math.min(Math.round(val), 100), 0)
	log.trace "volume = $v"

	deviceAction("/api/volume?volume=$v")

}
