@protocol CDVHeartBeatDetectionDelegate

- (void)heartRateStart;
- (void)heartRateUpdate:(int)bpm atTime:(int)seconds;
- (void)heartRateEnd;

@end

@interface CDVHeartBeatDetection : NSObject

@property (nonatomic, weak) id<CDVHeartBeatDetectionDelegate> delegate;

- (void)startDetection;
- (void)stopDetection;

@end