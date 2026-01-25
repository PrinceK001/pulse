import Foundation

/**
 * Allows React Native to override iOS ViewController-based screen tracking.
 */
@objc(ReactNativeScreenNameTracker)
public class ReactNativeScreenNameTracker: NSObject {
    private static let currentScreenName = NSLock()
    private static var _currentScreenName: String?
    
    @objc public static func setCurrentScreenName(_ screenName: String?) {
        currentScreenName.lock()
        defer { currentScreenName.unlock() }
        _currentScreenName = screenName
    }
    
    static func getCurrentScreenName() -> String? {
        currentScreenName.lock()
        defer { currentScreenName.unlock() }
        return _currentScreenName
    }
}

