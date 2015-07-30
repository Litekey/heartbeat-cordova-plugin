#import "CDVHeartBeatDetection.h"
#import <AVFoundation/AVFoundation.h>

@interface CDVHeartBeatDetection() <AVCaptureVideoDataOutputSampleBufferDelegate>

@property (nonatomic, strong) AVCaptureSession *session;
@property (nonatomic, strong) NSMutableArray *dataPointsHue;

@end

@implementation CDVHeartBeatDetection

#pragma mark - Data collection

- (void)startDetection
{
    self.dataPointsHue = [[NSMutableArray alloc] init];
    self.session = [[AVCaptureSession alloc] init];
    self.session.sessionPreset = AVCaptureSessionPresetLow;

    NSArray *devices = [AVCaptureDevice devices];
    AVCaptureDevice *captureDevice;
    for (AVCaptureDevice *device in devices)
    {
        if ([device hasMediaType:AVMediaTypeVideo])
        {
            if (device.position == AVCaptureDevicePositionBack)
            {
                captureDevice = device;
                break;
            }
        }
    }
    
    NSError *error;
    AVCaptureDeviceInput *input = [[AVCaptureDeviceInput alloc] initWithDevice:captureDevice error:&error];
    [self.session addInput:input];
    
    if (error)
    {
        NSLog(@"%@", error);
    }

    AVCaptureDeviceFormat *currentFormat;
    for (AVCaptureDeviceFormat *format in captureDevice.formats)
    {
        NSArray *ranges = format.videoSupportedFrameRateRanges;
        AVFrameRateRange *frameRates = ranges[0];
        
        if (frameRates.maxFrameRate == self.fps && (!currentFormat || (CMVideoFormatDescriptionGetDimensions(format.formatDescription).width < CMVideoFormatDescriptionGetDimensions(currentFormat.formatDescription).width && CMVideoFormatDescriptionGetDimensions(format.formatDescription).height < CMVideoFormatDescriptionGetDimensions(currentFormat.formatDescription).height)))
        {
            currentFormat = format;
        }
    }
    
    [captureDevice lockForConfiguration:nil];
    captureDevice.torchMode=AVCaptureTorchModeOn;
    captureDevice.activeFormat = currentFormat;
    captureDevice.activeVideoMinFrameDuration = CMTimeMake(1, self.fps);
    captureDevice.activeVideoMaxFrameDuration = CMTimeMake(1, self.fps);
    [captureDevice unlockForConfiguration];
    
    AVCaptureVideoDataOutput* videoOutput = [[AVCaptureVideoDataOutput alloc] init];
    
    dispatch_queue_t captureQueue=dispatch_queue_create("catpureQueue", NULL);
    
    [videoOutput setSampleBufferDelegate:self queue:captureQueue];
    videoOutput.videoSettings = [NSDictionary dictionaryWithObjectsAndKeys:[NSNumber numberWithUnsignedInt:kCVPixelFormatType_32BGRA], (id)kCVPixelBufferPixelFormatTypeKey,
                                 nil];
    videoOutput.alwaysDiscardsLateVideoFrames = NO;
    [self.session addOutput:videoOutput];
    [self.session startRunning];
    
    if (self.delegate)
    {
        dispatch_async(dispatch_get_main_queue(), ^{
            [self.delegate heartRateStart];
        });
    }
}

- (void)stopDetection
{
    [self.session stopRunning];
    
    if (self.delegate)
    {
        dispatch_async(dispatch_get_main_queue(), ^{
            [self.delegate heartRateEnd];
        });
    }
}

- (void)captureOutput:(AVCaptureOutput *)captureOutput didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer fromConnection:(AVCaptureConnection *)connection
{
    static int count=0;
    count++;
    CVImageBufferRef cvimgRef = CMSampleBufferGetImageBuffer(sampleBuffer);
    CVPixelBufferLockBaseAddress(cvimgRef,0);
    NSInteger width = CVPixelBufferGetWidth(cvimgRef);
    NSInteger height = CVPixelBufferGetHeight(cvimgRef);
    
    uint8_t *buf=(uint8_t *) CVPixelBufferGetBaseAddress(cvimgRef);
    size_t bprow=CVPixelBufferGetBytesPerRow(cvimgRef);
    float r=0,g=0,b=0;
    
    long widthScaleFactor = width/192;
    long heightScaleFactor = height/144;
    
    for(int y=0; y < height; y+=heightScaleFactor) {
        for(int x=0; x < width*4; x+=(4*widthScaleFactor)) {
            b+=buf[x];
            g+=buf[x+1];
            r+=buf[x+2];
        }
        buf+=bprow;
    }
    
    r/=255*(float) (width*height/widthScaleFactor/heightScaleFactor);
    g/=255*(float) (width*height/widthScaleFactor/heightScaleFactor);
    b/=255*(float) (width*height/widthScaleFactor/heightScaleFactor);
    
    //UIColor *color = [UIColor colorWithRed:r green:g blue:b alpha:1.0];
    float hue, sat, bright;
    
    RGBtoHSV(r, g, b, &hue, &sat, &bright);
    
    //[color getHue:&hue saturation:&sat brightness:&bright alpha:nil];
    
    [self.dataPointsHue addObject:@(hue)];
    
    if (self.dataPointsHue.count == self.fps * self.seconds)
    {
        if (self.delegate)
        {
            float displaySeconds = self.dataPointsHue.count / self.fps;
            
            NSArray *bandpassFilteredItems = butterworthBandpassFilter(self.dataPointsHue);
            NSArray *smoothedBandpassItems = medianSmoothing(bandpassFilteredItems);
            int peak = medianPeak(smoothedBandpassItems);
            int heartRate = 60 * self.fps / peak;
            
            dispatch_async(dispatch_get_main_queue(), ^{
                [self.delegate heartRateUpdate:heartRate atTime:displaySeconds];
            });
        
        }
    }
    
    CVPixelBufferUnlockBaseAddress(cvimgRef,0);
    
    if (self.dataPointsHue.count == (self.seconds * self.fps))
    {
        [self stopDetection];
    }
    
}

void RGBtoHSV( float r, float g, float b, float *h, float *s, float *v ) {
    float min, max, delta;
    min = MIN( r, MIN(g, b ));
    max = MAX( r, MAX(g, b ));
    *v = max;
    delta = max - min;
    if( max != 0 )
        *s = delta / max;
    else {
        *s = 0;
        *h = -1;
        return;
    }
    if( r == max )
        *h = ( g - b ) / delta;
    else if( g == max )
        *h=2+(b-r)/delta;
    else
        *h=4+(r-g)/delta;
    *h *= 60;
    if( *h < 0 )
        *h += 360;
}

#pragma mark - Data processing

// http://www-users.cs.york.ac.uk/~fisher/cgi-bin/mkfscript
// Butterworth Bandpass filter
NSArray * butterworthBandpassFilter(NSArray *inputData)
{
    const int NZEROS = 8;
    const int NPOLES = 8;
    static float xv[NZEROS+1], yv[NPOLES+1];
    
    double dGain = 1.232232910e+02;
    
    NSMutableArray *outputData = [[NSMutableArray alloc] init];
    for (NSNumber *number in inputData)
    {
        double input = number.doubleValue;
        
        xv[0] = xv[1]; xv[1] = xv[2]; xv[2] = xv[3]; xv[3] = xv[4]; xv[4] = xv[5]; xv[5] = xv[6]; xv[6] = xv[7]; xv[7] = xv[8];
        xv[8] = input / dGain;
        yv[0] = yv[1]; yv[1] = yv[2]; yv[2] = yv[3]; yv[3] = yv[4]; yv[4] = yv[5]; yv[5] = yv[6]; yv[6] = yv[7]; yv[7] = yv[8];
        yv[8] =   (xv[0] + xv[8]) - 4 * (xv[2] + xv[6]) + 6 * xv[4]
        + ( -0.1397436053 * yv[0]) + (  1.2948188815 * yv[1])
        + ( -5.4070037946 * yv[2]) + ( 13.2683981280 * yv[3])
        + (-20.9442560520 * yv[4]) + ( 21.7932169160 * yv[5])
        + (-14.5817197500 * yv[6]) + (  5.7161939252 * yv[7]);
        
        [outputData addObject:@(yv[8])];
    }
    
    return outputData;
}

int medianPeak(NSArray *inputData)
{
    NSMutableArray *peaks = [[NSMutableArray alloc] init];
    int count = 4;
    for (int i = 3; i < inputData.count - 3; i++,count++)
    {
        if (inputData[i] > 0 &&
            [inputData[i] doubleValue] > [inputData[i-1] doubleValue] &&
            [inputData[i] doubleValue] > [inputData[i-2] doubleValue] &&
            [inputData[i] doubleValue] > [inputData[i-3] doubleValue] &&
            [inputData[i] doubleValue] >= [inputData[i+1] doubleValue] &&
            [inputData[i] doubleValue] >= [inputData[i+2] doubleValue] &&
            [inputData[i] doubleValue] >= [inputData[i+3] doubleValue]
            )
        {
            [peaks addObject:@(count)];
            i += 3;
            count = 3;
        }
    }
    [peaks setObject:@([peaks[0] integerValue] + count + 3) atIndexedSubscript: 0];
    [peaks sortUsingComparator:^(NSNumber *a, NSNumber *b){
        return [a compare:b];
    }];
    int medianPeak = (int)[peaks[peaks.count * 2 / 3] integerValue];
    return medianPeak;
}

NSArray *medianSmoothing(NSArray *inputData)
{
    NSMutableArray *newData = [[NSMutableArray alloc] init];
    
    for (int i = 0; i < inputData.count; i++)
    {
        if (i == 0 ||
            i == 1 ||
            i == 2 ||
            i == inputData.count - 1 ||
            i == inputData.count - 2 ||
            i == inputData.count - 3)        {
            [newData addObject:inputData[i]];
        }
        else
        {
            NSArray *items = [@[
                                inputData[i-2],
                                inputData[i-1],
                                inputData[i],
                                inputData[i+1],
                                inputData[i+2],
                                ] sortedArrayUsingDescriptors:@[[NSSortDescriptor sortDescriptorWithKey:@"self" ascending:YES]]];
            
            [newData addObject:items[2]];
        }
    }
    
    return newData;
}

@end