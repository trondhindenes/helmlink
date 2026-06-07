import Toybox.Communications;
import Toybox.Lang;

(:glance)
module CommManager {

    function sendCommand(data as Dictionary) as Void {
        Communications.transmit(data, null, new CommListener());
    }
}

(:glance)
class CommListener extends Communications.ConnectionListener {

    function initialize() {
        ConnectionListener.initialize();
    }

    function onComplete() as Void {
    }

    function onError() as Void {
    }
}
