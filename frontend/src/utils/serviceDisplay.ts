const normalizeValue = (value?: string | null) => {
    if (typeof value !== 'string') {
        return '';
    }

    return value.trim();
};

export const getServiceDisplayName = (
    serviceName?: string | null,
    serviceCode?: string | null
) => normalizeValue(serviceName) || normalizeValue(serviceCode);
