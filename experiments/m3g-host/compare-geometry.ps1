param([Parameter(Mandatory=$true)][string]$Before,[Parameter(Mandatory=$true)][string]$After)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
public static class GeometryPixels {
    static int[] Read(string path) {
        using(var bitmap=new Bitmap(path)) {
            var bounds=new Rectangle(0,0,bitmap.Width,bitmap.Height);
            var data=bitmap.LockBits(bounds,ImageLockMode.ReadOnly,PixelFormat.Format32bppArgb);
            try {
                var pixels=new int[bitmap.Width*bitmap.Height];
                for(int y=0;y<bitmap.Height;y++) Marshal.Copy(IntPtr.Add(data.Scan0,y*data.Stride),pixels,y*bitmap.Width,bitmap.Width);
                return pixels;
            } finally { bitmap.UnlockBits(data); }
        }
    }
    public static long[] Compare(string first,string second) {
        int[] a=Read(first),b=Read(second);
        if(a.Length!=b.Length)throw new Exception("Size mismatch");
        long changed=0,beyond3=0,beyond8=0,beyond32=0,sum=0,max=0;
        for(int i=0;i<a.Length;i++) {
            int error=0;
            for(int shift=0;shift<32;shift+=8) {
                int delta=Math.Abs(((a[i]>>shift)&255)-((b[i]>>shift)&255));
                sum+=delta; error=Math.Max(error,delta);
            }
            if(error>0)changed++; if(error>3)beyond3++; max=Math.Max(max,error);
            if(error>8)beyond8++; if(error>32)beyond32++;
        }
        return new long[]{a.Length,changed,beyond3,max,sum,beyond8,beyond32};
    }
}
'@
$old=Get-Content -LiteralPath (Join-Path $Before 'report.json') -Raw | ConvertFrom-Json
$new=Get-Content -LiteralPath (Join-Path $After 'report.json') -Raw | ConvertFrom-Json
$rows=foreach($item in $new.cases) {
    $previous=$old.cases | Where-Object name -eq $item.name
    if($item.skipped -or $previous.skipped){continue}
    $diff=[GeometryPixels]::Compare((Join-Path $Before ($item.name+'.png')),(Join-Path $After ($item.name+'.png')))
    $beforeMs=($previous.wallNs | Measure-Object -Average).Average/$previous.framesPerBatch/1e6
    $afterMs=($item.wallNs | Measure-Object -Average).Average/$item.framesPerBatch/1e6
    [pscustomobject]@{scene=$item.name;beforeMs=$beforeMs;afterMs=$afterMs;ratio=$afterMs/$beforeMs;
        pixels=$diff[0];changed=$diff[1];beyond3=$diff[2];maxError=$diff[3];meanChannelError=$diff[4]/($diff[0]*4);
        beyond8=$diff[5];beyond32=$diff[6];
        hardwareDraws=$item.gpuHardwareDraws;cpuShadedVertices=$item.shadedVertices}
}
$rows | ConvertTo-Json -Depth 4
