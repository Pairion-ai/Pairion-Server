#!/usr/bin/env python3
"""
Kokoro-MLX TTS sidecar script.

Synthesizes text to raw PCM audio using MLX-Audio's Kokoro-82M model.
Outputs raw 16-bit signed PCM to stdout.

Usage: python3 kokoro_tts.py --text "Hello world" --voice bm_george --sample-rate 24000

Requires: pip install mlx-audio
"""

import argparse
import sys

def main():
    parser = argparse.ArgumentParser(description="Kokoro-MLX TTS")
    parser.add_argument("--text", required=True, help="Text to synthesize")
    parser.add_argument("--voice", default="bm_george", help="Voice ID")
    parser.add_argument("--sample-rate", type=int, default=24000, help="Output sample rate")
    args = parser.parse_args()

    try:
        from mlx_audio.tts import generate
        import numpy as np

        audio = generate(args.text, voice=args.voice, speed=1.0)
        pcm = (audio * 32767).astype(np.int16)
        sys.stdout.buffer.write(pcm.tobytes())
    except ImportError:
        print("mlx-audio not installed. Install with: pip install mlx-audio", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
