@protocol CDVHeartBeatDetectionDelegate

- (void)heartRateStart;
- (void)heartRateUpdate:(int)bpm atTime:(int)seconds;
- (void)heartRateEnd;

@end

@interface CDVHeartBeatDetection : NSObject

@property (nonatomic, weak) id<CDVHeartBeatDetectionDelegate> delegate;
@property (nonatomic, assign) int seconds;
@property (nonatomic, assign) int fps;

- (void)startDetection;
- (void)stopDetection;

@end