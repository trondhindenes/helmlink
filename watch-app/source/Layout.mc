import Toybox.Lang;
import Toybox.System;

// Shared screen geometry used by both the view (drawing) and the delegate
// (tap hit-testing). All positions are scaled from the FR165's 390x390
// reference layout so the same proportions work on other screen sizes.
module Layout {

    function screenWidth() as Number {
        return System.getDeviceSettings().screenWidth;
    }

    function screenHeight() as Number {
        return System.getDeviceSettings().screenHeight;
    }

    // Scale a horizontal value from the 390px-wide reference layout
    function scaleX(x as Number) as Number {
        return x * screenWidth() / 390;
    }

    // Scale a vertical value from the 390px-tall reference layout
    function scaleY(y as Number) as Number {
        return y * screenHeight() / 390;
    }

    // Increment selector button geometry
    function btnW() as Number { return scaleX(75); }
    function btnH() as Number { return scaleY(32); }
    function btnY() as Number { return scaleY(225); }
    function btn1X() as Number { return screenWidth() / 2 - btnW() - scaleX(5); }
    function btn10X() as Number { return screenWidth() / 2 + scaleX(5); }
}
