#!/bin/bash
# Copyright (C) 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

[ $(basename $PWD) != "gitiles" ] && echo "This script must be run from the gitiles top directory" && exit 1

PROJECT_PY_PATH=$(bazel query @com_googlesource_gerrit_bazlets//tools/eclipse:project --output location | sed s/BUILD:.*//)
[ $? -ne 0 ] && echo "Unable fo find project.py" && exit 1

$PROJECT_PY_PATH/project.py -n gitiles -r .
[ $? -eq 0 ] && echo "Eclipse configuration generated."

