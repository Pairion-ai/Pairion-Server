#!/usr/bin/env python3
"""
Whisper-MLX STT sidecar script.

Transcribes raw PCM audio to text using Whisper via MLX.
Outputs JSON: { "text": "transcribed text" }

Usage: python3 whisper_stt.py --audio /path/to/audio.raw --model small --sample-rate 16000

Requires: pip install mlx-whisper
"""

import argparse
import json
import sys

def main():
    parser = argparse.ArgumentParser(description="Whisper-MLX STT")
    parser.add_argument("--audio", required=True, help="Path to raw PCM audio file")
    parser.add_argument("--model", default="small", help="Whisper model size")
    parser.add_argument("--sample-rate", type=int, default=16000, help="Audio sample rate")
    args = parser.parse_args()

    try:
        import mlx_whisper
        import numpy as np

        raw = open(args.audio, "rb").read()
        audio = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
        result = mlx_whisper.transcribe(audio, path_or_hf_repo=f"mlx-community/whisper-{args.model}-mlx")
        print(json.dumps({"text": result["text"]}))
    except ImportError:
        print("mlx-whisper not installed. Install with: pip install mlx-whisper", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
