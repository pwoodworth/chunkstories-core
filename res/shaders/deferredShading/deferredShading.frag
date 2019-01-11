#version 450

in vec2 vertexPos;

out vec4 colorOut;

uniform sampler2D colorBuffer;
uniform sampler2D normalBuffer;
uniform sampler2D depthBuffer;

#include ../gamma.glsl

#include struct <xyz.chunkstories.api.graphics.structs.Camera>
uniform Camera camera;

#include struct <xyz.chunkstories.api.graphics.structs.WorldConditions>
uniform WorldConditions world;

vec4 convertScreenSpaceToCameraSpace(vec2 screenSpaceCoordinates, sampler2D depthBuffer)
{
    vec4 cameraSpacePosition = camera.projectionMatrixInverted * vec4(vec3(screenSpaceCoordinates * 2.0 - vec2(1.0), texture(depthBuffer, screenSpaceCoordinates, 0.0).x), 1.0);
    cameraSpacePosition /= cameraSpacePosition.w;
    return cameraSpacePosition;
}

void main()
{
	vec2 texCoord = vec2(vertexPos.x * 0.5 + 0.5, 0.5 + vertexPos.y * 0.5);
	vec4 albedo = pow(texture(colorBuffer, texCoord), vec4(2.1));
	vec3 normal = texture(normalBuffer, texCoord).xyz * 2.0 - vec3(1.0);

	vec4 cameraSpacePosition = convertScreenSpaceToCameraSpace(texCoord, depthBuffer);
	vec4 worldSpacePosition = camera.viewMatrixInverted * cameraSpacePosition;

	if(albedo.a == 0) {
		discard;
	}

	vec2 color = vec2(1.0, 0.0);

	float NdL = clamp(dot(world.sunPosition, normal.xyz), 0.0, 1.0);
	
	vec3 shadowLight = pow(vec3(52.0 / 255.0, 68.0 / 255.0, 84.0 / 255.0), vec3(gamma));
	vec3 sunLight = vec3(1.0) - shadowLight;

	vec3 lightColor = (NdL * sunLight + shadowLight) * color.x;
	lightColor += vec3(1.0) * pow(color.y, 2.0);

	vec3 litSurface = albedo.rgb * lightColor;
	//litSurface = camera.lookingAt.rgb;
	//litSurface = normal.rgb;
	//vec3 E = camera.lookingAt.xyz;
	//E = normalize(vertex - camera.position);
	//vec3 R = reflect(E, normal);
	//litSurface = clamp(dot(R, world.sunPosition), 0.0, 1.0) * vec3(1.0);
	//litSurface = R;
	//litSurface = getSkyColor(0.5, E);

	vec3 fog = vec3(0.0, 0.5, 1.0);
	
	float fogStrength = clamp(length(worldSpacePosition.xyz - camera.position.xyz) * 0.001, 0.0, 1.0);

	//litSurface *= 0.1;
	//litSurface.xyz += worldSpacePosition.xyz / vec3(4096.0);

	colorOut = vec4(mix(litSurface, fog, fogStrength), albedo.a);
}