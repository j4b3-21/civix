"""S3-compatible object storage (MinIO locally). Objects are written with server-side encryption."""

from __future__ import annotations

import asyncio
from typing import Protocol

import boto3
from botocore.config import Config


class ObjectStorage(Protocol):
    async def get(self, key: str) -> bytes: ...
    async def put(self, key: str, data: bytes, content_type: str) -> None: ...
    async def ping(self) -> bool: ...


class S3Storage:
    def __init__(self, *, endpoint: str | None, region: str, bucket: str, access_key: str, secret_key: str,
                 force_path_style: bool = True, sse: str | None = "AES256") -> None:
        self._bucket = bucket
        self._sse = sse
        self._client = boto3.client(
            "s3",
            endpoint_url=endpoint or None,
            region_name=region,
            aws_access_key_id=access_key or None,
            aws_secret_access_key=secret_key or None,
            config=Config(s3={"addressing_style": "path" if force_path_style else "auto"}, retries={"max_attempts": 3},
                          signature_version="s3v4"),
        )

    async def get(self, key: str) -> bytes:
        def _get() -> bytes:
            return self._client.get_object(Bucket=self._bucket, Key=key)["Body"].read()

        return await asyncio.to_thread(_get)

    async def put(self, key: str, data: bytes, content_type: str) -> None:
        extra = {"ServerSideEncryption": self._sse} if self._sse else {}
        await asyncio.to_thread(self._client.put_object, Bucket=self._bucket, Key=key, Body=data, ContentType=content_type, **extra)

    async def ping(self) -> bool:
        try:
            await asyncio.to_thread(self._client.head_bucket, Bucket=self._bucket)
            return True
        except Exception:  # noqa: BLE001 - health probe
            return False


class MemoryStorage:
    """In-process storage for automated tests and local runs without object storage."""

    def __init__(self) -> None:
        self.objects: dict[str, tuple[bytes, str]] = {}

    async def get(self, key: str) -> bytes:
        if key not in self.objects:
            raise KeyError(key)
        return self.objects[key][0]

    async def put(self, key: str, data: bytes, content_type: str) -> None:
        self.objects[key] = (data, content_type)

    async def ping(self) -> bool:
        return True
