// SPDX-License-Identifier: GPL-3.0-only
package wolphun.vm;

/** Mophun BEEP records: little-endian frequency/duration and an unsigned volume. */
final class BeepSequence {
    private final int[] frequencies, volumes;
    private final double[] lengths;
    private final boolean loop;
    private int index;
    private double position;
    private boolean finished;

    BeepSequence(byte[] data, boolean loop) {
        if (data.length == 0 || data.length % 5 != 0)
            throw new IllegalArgumentException("Invalid BEEP sequence");
        int count=data.length/5;
        frequencies=new int[count]; volumes=new int[count]; lengths=new double[count];
        boolean audibleDuration=false;
        for(int i=0;i<count;i++) {
            int p=i*5;
            frequencies[i]=(data[p]&255)|((data[p+1]&255)<<8);
            lengths[i]=((data[p+2]&255)|((data[p+3]&255)<<8)) * (VSound.OUT_RATE/1000.0);
            volumes[i]=data[p+4]&255;
            audibleDuration |= lengths[i]>0;
        }
        this.loop=loop;
        finished=!audibleDuration;
    }

    BeepSequence(int frequency, int duration) {
        frequencies=new int[]{frequency}; volumes=new int[]{128};
        lengths=new double[]{Integer.toUnsignedLong(duration)*(VSound.OUT_RATE/1000.0)};
        loop=false; finished=frequency==0 || duration==0;
    }

    int next(double step) {
        if(finished)return 0;
        while(position>=lengths[index]) {
            position-=lengths[index];
            if(++index==lengths.length) {
                if(!loop){finished=true;return 0;}
                index=0;
            }
        }
        int frequency=frequencies[index];
        int sample=frequency>0 && frequency<VSound.OUT_RATE/2
                ? (int)(Math.sin(position*2*Math.PI*frequency/VSound.OUT_RATE)*16383*volumes[index]/255.0) : 0;
        position+=step;
        return sample;
    }

    boolean finished() { return finished; }
}
