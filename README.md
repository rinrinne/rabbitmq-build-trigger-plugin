rabbitmq-build-trigger: RabbitMQ Build Trigger Plugin for Jenkins
=======================================================

* Author: rinrinne a.k.a. rin_ne
* Repository: http://github.com/jenkinsci/rabbitmq-build-trigger-plugin
* Plugin Information: https://wiki.jenkins-ci.org/display/JENKINS/RabbitMQ+Build+Trigger+Plugin

Synopsis
------------------------

rabbitmq-build-trigger is a Jenkins plugin to trigger build using application message for remote build in specific queue on RabbitMQ.

Usage
------------------------

You need to install [RabbitMQ Consumer Plugin][rabbitmq-consumer] and configure it before using this plugin.

If you install this, *RabbitMQ Build Trigger* setting is added into your job project's build trigger section. please enable it then set your token. So build would be triggered if appropriate application message arrives.

Application Message Format
------------------------

A message must have two properties.

>  content_type: application/json
>  app_id: remote-build

```json
{
    "project": "RPROJECTNAME", 
    "token": "TOKEN",
    "parameter": [
        {
            "name": "PARAMETERNAME", 
            "value": "VALUE"
        },
        {
            "name": "PARAMETERNAME2",
            "value": "VALUE2"
        }
    ] 
}
```

name in each parameters is compared with existing parameter name by case-insensitive.

Material
------------------------

* [RabbitMQ Consumer Plugin][rabbitmq-consumer]

[rabbitmq-consumer]: http://wiki.jenkins-ci.org/display/JENKINS/RabbitMQ+Consumer+Plugin

License
------------------------

MIT License

Copyright
------------------------

Copyright (c) 2013 rinrinne a.k.a. rin_ne
