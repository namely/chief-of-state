#!/bin/bash

apt-get update

apt-get install -y \
    dpkg-sig \
    lintian \
    fakeroot
