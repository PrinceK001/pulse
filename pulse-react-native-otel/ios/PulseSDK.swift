import Foundation
import PulseKit
import OpenTelemetryApi
import OpenTelemetrySdk

@objc(PulseSDK)
public class PulseSDK: NSObject {
    
    // Swift-only method (not exposed to Objective-C because closures can't be represented in ObjC)
    public static func initialize(
        endpointBaseUrl: String,
        endpointHeaders: [String: String]?,
        globalAttributes: [String: PulseAttributeValue]?,
        instrumentations: ((inout InstrumentationConfiguration) -> Void)? = nil,
        tracerProviderCustomizer: ((TracerProviderBuilder) -> TracerProviderBuilder)? = nil,
        loggerProviderCustomizer: (([LogRecordProcessor]) -> [LogRecordProcessor])? = nil
    ) {
        let convertedAttributes: [String: AttributeValue]? = globalAttributes?.toSwiftAttributes()
        
        let rnTracerProviderCustomizer: ((TracerProviderBuilder) -> TracerProviderBuilder) = { builder in
            return builder.add(spanProcessor: ReactNativeScreenAttributesSpanProcessor())
        }
        
        let mergedTracerProviderCustomizer: ((TracerProviderBuilder) -> TracerProviderBuilder)? = 
            if let userCustomizer = tracerProviderCustomizer {
                { builder in
                    let builderWithRn = rnTracerProviderCustomizer(builder)
                    return userCustomizer(builderWithRn)
                }
            } else {
                rnTracerProviderCustomizer
            }
        
        let rnLoggerProviderCustomizer: (([LogRecordProcessor]) -> [LogRecordProcessor]) = { processors in
            guard let lastIndex = processors.indices.last else {
                return processors
            }
            var modified = processors
            modified[lastIndex] = ReactNativeScreenAttributesLogRecordProcessor(nextProcessor: processors[lastIndex])
            return modified
        }
        
        let mergedLoggerProviderCustomizer: (([LogRecordProcessor]) -> [LogRecordProcessor])? = 
            if let userCustomizer = loggerProviderCustomizer {
                { processors in
                    let withRn = rnLoggerProviderCustomizer(processors)
                    return userCustomizer(withRn)
                }
            } else {
                rnLoggerProviderCustomizer
            }
        
        PulseKit.shared.initialize(
            endpointBaseUrl: endpointBaseUrl,
            endpointHeaders: endpointHeaders,
            globalAttributes: convertedAttributes,
            instrumentations: instrumentations,
            tracerProviderCustomizer: mergedTracerProviderCustomizer,
            loggerProviderCustomizer: mergedLoggerProviderCustomizer
        )
    }
    
    @objc(initializeWithEndpointBaseUrl:endpointHeaders:globalAttributes:)
    public static func initialize(
        endpointBaseUrl: String,
        endpointHeaders: [String: String]?,
        globalAttributes: [String: PulseAttributeValue]?
    ) {
        initialize(
            endpointBaseUrl: endpointBaseUrl,
            endpointHeaders: endpointHeaders,
            globalAttributes: globalAttributes,
            instrumentations: nil,
            tracerProviderCustomizer: nil,
            loggerProviderCustomizer: nil
        )
    }
    
    @objc(initializeWithEndpointBaseUrl:)
    public static func initialize(endpointBaseUrl: String) {
        initialize(
            endpointBaseUrl: endpointBaseUrl,
            endpointHeaders: nil,
            globalAttributes: nil,
            instrumentations: nil,
            tracerProviderCustomizer: nil,
            loggerProviderCustomizer: nil
        )
    }
    
    @objc public static func isSDKInitialized() -> Bool {
        return PulseKit.shared.isSDKInitialized()
    }
    
    @objc(setUserId:)
    public static func setUserId(_ userId: String?) {
        PulseKit.shared.setUserId(userId)
    }
    
    @objc(setUserProperty:value:)
    public static func setUserProperty(name: String, value: PulseAttributeValue?) {
        PulseKit.shared.setUserProperty(name: name, value: value?.swiftValue)
    }
    
    @objc(setUserProperties:)
    public static func setUserProperties(_ properties: [String: PulseAttributeValue]) {
        PulseKit.shared.setUserProperties(properties.toSwiftAttributes())
    }
    
    @objc(trackEventWithName:observedTimeStampInMs:params:)
    public static func trackEvent(
        name: String,
        observedTimeStampInMs: Double,
        params: [String: PulseAttributeValue]
    ) {
        PulseKit.shared.trackEvent(
            name: name,
            observedTimeStampInMs: observedTimeStampInMs,
            params: params.toSwiftAttributes()
        )
    }
    
    @objc(trackNonFatalWithName:observedTimeStampInMs:params:)
    public static func trackNonFatal(
        name: String,
        observedTimeStampInMs: Int64,
        params: [String: PulseAttributeValue]
    ) {
        PulseKit.shared.trackNonFatal(
            name: name,
            observedTimeStampInMs: observedTimeStampInMs,
            params: params.toSwiftAttributes()
        )
    }
    
    public static func startSpan(
        name: String,
        params: [String: PulseAttributeValue] = [:]
    ) -> Span {
        return PulseKit.shared.startSpan(
            name: name,
            params: params.toSwiftAttributes()
        )
    }
    
    public static func trackSpan<T>(
        name: String,
        params: [String: PulseAttributeValue] = [:],
        action: () throws -> T
    ) rethrows -> T {
        return try PulseKit.shared.trackSpan(
            name: name,
            params: params.toSwiftAttributes(),
            action: action
        )
    }
    
    static func getOtelOrThrow() -> OpenTelemetry {
        return PulseKit.shared.getOtelOrThrow()
    }
}
