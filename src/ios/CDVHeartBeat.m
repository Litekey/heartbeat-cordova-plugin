#import "CDVHeartBeat.h"
#import "CDVHeartBeatDetection.h"

@interface CDVHeartBeat()<CDVHeartBeatDetectionDelegate>

@property (nonatomic, assign) bool detecting;
@property (nonatomic, strong) NSMutableArray *bpms;

@end

@implementation CDVHeartBeat


- (void)take:(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate runInBackground: ^{
    
        NSString* callbackId = [command callbackId];
        NSArray* arguments = command.arguments;
        
        CDVHeartBeatDetection* heartBeatDetection = [[CDVHeartBeatDetection alloc] init];
        heartBeatDetection.delegate = self;
        heartBeatDetection.seconds = [[arguments objectAtIndex:0] intValue];
        heartBeatDetection.fps = [[arguments objectAtIndex:1] intValue];
        self.detecting = true;
        [heartBeatDetection startDetection];
        
        while(self.detecting){
            
        }
        
        [self.bpms sortedArrayUsingDescriptors:@[[NSSortDescriptor sortDescriptorWithKey:@"self" ascending:YES]]];
        
        int bpm = [((NSNumber*)self.bpms[self.bpms.count/2]) intValue];
        
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK
                                   messageAsInt:bpm];
        
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    
    }];
}

- (void)heartRateStart{
    self.bpms = [[NSMutableArray alloc] init];
}

- (void)heartRateUpdate:(int)bpm atTime:(int)seconds{
    [self.bpms addObject:[NSNumber numberWithInt:bpm]];}

- (void)heartRateEnd{
    self.detecting = false;
}

@end